# Развёртывание microloan-archetype в Yandex Cloud (КТ-14)

Журнал всех выполненных команд и реестр созданных ресурсов.
Пароли и токены в этот файл не записываются: пароль БД хранится локально в
`deploy/secrets/db_password` (каталог в `.gitignore`), на ВМ — в `/etc/microloan/gateway.env` (права 600).

## Реестр созданных ресурсов

Удалять после защиты **только** ресурсы из этой таблицы. Все они помечены меткой `project=kt14-microloan`.

| # | Тип | Имя | ID | Зона / сеть | Платный |
|---|-----|-----|----|-------------|---------|
| 1 | Security group | microloan-vm-sg | enp0n49g5hglv6ga473o | сеть default (enp7ph5j712afp8ndlgk) | нет |
| 2 | Security group | microloan-db-sg | enpjrkf881eas5cgjg1s | сеть default (enp7ph5j712afp8ndlgk) | нет |
| 3 | Managed PostgreSQL cluster | microloan-pg | c9qfksvl5cbq9kvalkc8 | ru-central1-e, хост rc1e-f25g84kjj86u4fn8.mdb.yandexcloud.net (10.131.0.20) | **да** |
| 4 | Compute instance (ВМ) | microloan-vm | bg063g36jjans7lr9reu | ru-central1-e, внутр. IP 10.131.0.34 | **да** |
| 5 | Загрузочный диск ВМ (auto-delete) | — | bg0icdv2o5esi1qim23b | ru-central1-e, network-hdd 20 ГБ | **да** |
| 6 | Публичный IP (динамический, на ВМ) | — | 153.51.115.77 | ru-central1-e | **да** |

Удаление после защиты (в таком порядке):

```bash
yc compute instance delete bg063g36jjans7lr9reu            # ВМ + её диск + динамический IP
yc managed-postgresql cluster delete c9qfksvl5cbq9kvalkc8  # кластер БД вместе с данными
yc vpc security-group delete enpjrkf881eas5cgjg1s          # microloan-db-sg
yc vpc security-group delete enp0n49g5hglv6ga473o          # microloan-vm-sg
```

Используются, но **не созданы** в этой работе (не удалять): сеть `default` (enp7ph5j712afp8ndlgk),
подсеть `default-ru-central1-e` (ajcuuhqgaqrdi3qrla4a, 10.131.0.0/24).

## Исходное состояние облака

- Профиль `default`, folder-id `b1gui733edqs29khkqt3`.
- Уже существовали: ВМ `nginx-web-vm`, `cl1uq817bltb5m4d6isr-ihov` (узел группы ВМ `test-instance-group`),
  `demo-1`, `demo-2`; сети `default` и `custom-vpc-network`; группа `nginx-security-group`.
- Из-за квот (см. шаг 4) **по явному разрешению владельца** удалены неиспользуемые ресурсы прошлых работ:
  ВМ `nginx-web-vm` (fhmuttb9n5577gtpqjde), группа ВМ `test-instance-group` (cl1uq817bltb5m4d6isr)
  вместе с ВМ `cl1uq817bltb5m4d6isr-ihov`, ВМ `demo-2` (bg0pmqb0oujp6af3kkth) — с их загрузочными дисками.
  `demo-1`, сеть `custom-vpc-network`, `nginx-security-group`, диск `snapshot-before-update` не тронуты.

## Шаг 0. Подготовка приложения и секрета

Настройки БД вынесены в переменные окружения (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`,
`DB_PASSWORD`, `DB_SSLMODE`), см. `loan-gateway/src/main/resources/application.yml` и `.env.example`.

```bash
# сборка обоих сервисов (JAVA_HOME = JDK 21)
mvn -DskipTests package

# генерация пароля БД в файл, который не попадает в git
mkdir -p deploy/secrets
py -c "import secrets,string;a=string.ascii_letters+string.digits;print(''.join(secrets.choice(a) for _ in range(24)),end='')" > deploy/secrets/db_password
```

## Шаг 1. Сеть

```bash
yc vpc network create --name microloan-net --description "KT-14 microloan" --labels project=kt14-microloan
# -> ERROR: ResourceExhausted, Quota limit vpc.networks.count exceeded (limit 2, usage 2)
```

Квота на сети исчерпана (лимит 2). Решение: использовать существующую сеть `default` и её подсеть
`default-ru-central1-e` (ничего в них не меняя). Зона `ru-central1-e` выбрана потому, что класс
хостов `b2.medium` Managed PostgreSQL доступен только в зонах `ru-central1-d` и `ru-central1-e`.

## Шаг 2. Группы безопасности

```bash
# ВМ: SSH и порт приложения извне, исходящий трафик любой
yc vpc security-group create --name microloan-vm-sg --network-id enp7ph5j712afp8ndlgk \
  --description "KT-14: SSH + app port" --labels project=kt14-microloan \
  --rule "direction=ingress,port=22,protocol=tcp,v4-cidrs=[0.0.0.0/0],description=ssh" \
  --rule "direction=ingress,port=8081,protocol=tcp,v4-cidrs=[0.0.0.0/0],description=loan-gateway" \
  --rule "direction=egress,from-port=0,to-port=65535,protocol=any,v4-cidrs=[0.0.0.0/0],description=all-out"

# БД: порт 6432 (пулер Odyssey) только от ВМ из группы microloan-vm-sg
yc vpc security-group create --name microloan-db-sg --network-id enp7ph5j712afp8ndlgk \
  --description "KT-14: PostgreSQL 6432 only from microloan-vm-sg" --labels project=kt14-microloan \
  --rule "direction=ingress,port=6432,protocol=tcp,security-group-id=enp0n49g5hglv6ga473o,description=pg-from-vm"
```

## Шаг 3. Кластер Managed Service for PostgreSQL

```bash
# пароль подставляется из локального файла и не печатается
yc managed-postgresql cluster create \
  --name microloan-pg --description "KT-14 microloan DB" --labels project=kt14-microloan \
  --environment production --network-id enp7ph5j712afp8ndlgk \
  --host zone-id=ru-central1-e,subnet-id=ajcuuhqgaqrdi3qrla4a,assign-public-ip=false \
  --resource-preset b2.medium --disk-type network-ssd --disk-size 10 \
  --postgresql-version 16 --security-group-ids enpjrkf881eas5cgjg1s \
  --user name=loan_admin,password="$(cat deploy/secrets/db_password)" \
  --database name=loan_db,owner=loan_admin \
  --async
# операция c9q6nedrmjm1dgh0nj52, ~10 минут
yc operation wait c9q6nedrmjm1dgh0nj52

# проверка
yc managed-postgresql cluster get c9qfksvl5cbq9kvalkc8        # status RUNNING
yc managed-postgresql hosts list --cluster-id c9qfksvl5cbq9kvalkc8
yc managed-postgresql database list --cluster-id c9qfksvl5cbq9kvalkc8
yc managed-postgresql user list --cluster-id c9qfksvl5cbq9kvalkc8
```

Результат: кластер `RUNNING`, хост MASTER `ALIVE` без публичного IP, база `loan_db` (owner `loan_admin`),
лимит соединений пользователя 50.

Строка подключения для приложения (через пулер Odyssey):
`jdbc:postgresql://c-c9qfksvl5cbq9kvalkc8.rw.mdb.yandexcloud.net:6432/loan_db?sslmode=require`
(`c-<cluster_id>.rw...` — особое FQDN, всегда указывает на текущий мастер).

## Шаг 4. Виртуальная машина

```bash
# cloud-init (deploy/cloud-init.yaml) с публичным ключом рендерится в gitignored-файл
KEY="$(cat ~/.ssh/id_ed25519.pub)"
sed "s|__SSH_PUBLIC_KEY__|$KEY|" deploy/cloud-init.yaml > deploy/secrets/cloud-init.rendered.yaml
```

### Неудачные попытки (ресурсы не создавались)

| # | Зона, диск | Ошибка |
|---|------------|--------|
| 1 | ru-central1-e, network-hdd 20 ГБ | `ResourceExhausted: The limit on total size of network-hdd disks has exceeded for zone "ru-central1-e"` |
| 2 | ru-central1-b (subnet e2lvaahpe47grfn8gdtu) | `FailedPrecondition: Zone ru-central1-b is not available` |
| 3 | ru-central1-d (subnet fl8fgckcerro8sl93qi3) | `ResourceExhausted: The limit on maximum number of cores has exceeded for zone "ru-central1-d"` |
| 4 | ru-central1-a (subnet e9btigpga5d57tb5g9nn) | `ResourceExhausted: Resource allocation is restricted` |
| 5 | ru-central1-e, network-ssd 20 ГБ | `ResourceExhausted: The limit on total size of network-ssd disks has exceeded for zone "ru-central1-e"` |
| 6 | ru-central1-a после удаления nginx-web-vm | `ResourceExhausted: Resource allocation is restricted` (ограничение зоны, не квота) |

Диагностика квот (только чтение):

```bash
yc quota-manager quota-limit list --service compute --resource-type resource-manager.cloud --resource-id b1gp4rle1ph8ldb9prd6
```

Лимиты: **2 vCPU на зону**; HDD a=20, d=20, e=30 ГБ; SSD a=15, d=30, e=10 ГБ. В зоне e было занято 4 vCPU
(`demo-2` + узел группы `test-instance-group`). Узел группы ВМ нельзя удалить отдельно — группа фиксированного
размера пересоздаст его, поэтому удалялась группа целиком.

```bash
# по явному разрешению владельца — неиспользуемые ресурсы прошлых работ
yc compute instance delete fhmuttb9n5577gtpqjde          # nginx-web-vm (зона a) + диск fhm56qlimbd51i94528m
yc compute instance-group delete cl1uq817bltb5m4d6isr    # test-instance-group + ВМ cl1uq817bltb5m4d6isr-ihov
yc compute instance delete bg0pmqb0oujp6af3kkth          # demo-2 (зона e) + диск bg08sag9bgmbo4qd2at6
```

### Успешное создание

```bash
yc compute instance create --name microloan-vm --description "KT-14 microloan app" --labels project=kt14-microloan \
  --zone ru-central1-e --platform standard-v3 --cores 2 --core-fraction 20 --memory 4 \
  --create-boot-disk image-folder-id=standard-images,image-family=ubuntu-2404-lts,type=network-hdd,size=20 \
  --network-interface subnet-id=ajcuuhqgaqrdi3qrla4a,nat-ip-version=ipv4,security-group-ids=enp0n49g5hglv6ga473o \
  --metadata-from-file user-data=deploy/secrets/cloud-init.rendered.yaml
# -> id bg063g36jjans7lr9reu, RUNNING, внутр. 10.131.0.34, публичный 153.51.115.77

# ожидание cloud-init и проверка установленного ПО
ssh -i ~/.ssh/id_ed25519 yc-user@153.51.115.77 'cloud-init status --wait; java -version; docker --version; psql --version; id microloan'
# -> status: done; openjdk 21.0.12.1; Docker 29.1.3; psql 16.15; системный пользователь microloan
```

## Шаг 5. Загрузка приложения и секрета на ВМ

```bash
IP=153.51.115.77
scp -i ~/.ssh/id_ed25519 loan-gateway/target/loan-gateway-0.0.1-SNAPSHOT.jar yc-user@$IP:/tmp/loan-gateway.jar
scp -i ~/.ssh/id_ed25519 scoring-service/target/scoring-service-0.0.1-SNAPSHOT.jar yc-user@$IP:/tmp/scoring-service.jar
scp -i ~/.ssh/id_ed25519 deploy/kafka-container.service deploy/microloan-gateway.service deploy/microloan-scoring.service yc-user@$IP:/tmp/

ssh -i ~/.ssh/id_ed25519 yc-user@$IP 'sudo install -o root -g microloan -m 640 /tmp/loan-gateway.jar /tmp/scoring-service.jar /opt/microloan/ \
  && sudo install -m 644 /tmp/*.service /etc/systemd/system/ && rm /tmp/*.jar /tmp/*.service'

# env-файл с настройками БД; пароль передаётся через stdin (не попадает в аргументы и вывод)
{ printf 'DB_HOST=c-c9qfksvl5cbq9kvalkc8.rw.mdb.yandexcloud.net\nDB_PORT=6432\nDB_NAME=loan_db\nDB_USER=loan_admin\nDB_SSLMODE=require\nDB_POOL_SIZE=10\nDB_POOL_MIN_IDLE=2\nJPA_SHOW_SQL=false\nSERVER_PORT=8081\nKAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092\nDB_PASSWORD='; \
  cat deploy/secrets/db_password; printf '\n'; } \
  | ssh -i ~/.ssh/id_ed25519 yc-user@$IP 'sudo install -o root -g root -m 600 /dev/stdin /etc/microloan/gateway.env'
```

## Шаг 6. Запуск сервисов systemd

Юниты (в репозитории, `deploy/`):

- `kafka-container.service` — Kafka 7.6.0 (KRaft, 1 узел) в Docker, порт только `127.0.0.1:9092`, heap 512 МБ, `Restart=always`;
- `microloan-scoring.service` — scoring-service (порт 8082, наружу закрыт), `-Xmx256m`;
- `microloan-gateway.service` — loan-gateway (порт 8081), `EnvironmentFile=/etc/microloan/gateway.env`, `-Xmx384m`.

Приложения запускаются от системного пользователя `microloan`, `Restart=on-failure`; все три юнита
включены в автозапуск (`WantedBy=multi-user.target`).

```bash
ssh -i ~/.ssh/id_ed25519 yc-user@153.51.115.77 '
  sudo systemctl daemon-reload
  sudo systemctl enable --now kafka-container
  sudo docker exec kafka kafka-topics --bootstrap-server 127.0.0.1:9092 --list   # ждём готовности Kafka
  sudo systemctl enable --now microloan-scoring microloan-gateway
  systemctl is-active kafka-container microloan-scoring microloan-gateway        # active x3
  curl -s localhost:8081/actuator/health                                         # {"status":"UP", db: UP (PostgreSQL)}
  sudo journalctl -u microloan-gateway -n 50'
```

## Шаг 7. Проверка

```bash
IP=153.51.115.77
curl http://$IP:8081/actuator/health
# -> {"status":"UP","components":{"db":{"status":"UP","details":{"database":"PostgreSQL",...

curl -X POST http://$IP:8081/api/v1/loans -H 'Content-Type: application/json' \
  -d '{"name":"Ivan Ivanov","passport":"1234567890","email":"ivan@example.com","loanAmount":250000.00,"monthsTime":12}'
# -> 201, "id":"d1fee28d-0190-41f9-99c5-d3f67246e3cb", "status":"CREATED"

curl http://$IP:8081/api/v1/loans/d1fee28d-0190-41f9-99c5-d3f67246e3cb
# -> "status":"APPROVED" (через ~1 с: gateway -> Kafka -> scoring-service -> Kafka -> gateway -> БД)

curl -m 5 http://$IP:8082/      # -> таймаут: порт scoring-service снаружи закрыт группой безопасности

# данные в Managed PostgreSQL (psql с ВМ, параметры из env-файла)
ssh -i ~/.ssh/id_ed25519 yc-user@$IP 'sudo bash -c "set -a; . /etc/microloan/gateway.env; set +a;
  PGPASSWORD=\$DB_PASSWORD psql \"host=\$DB_HOST port=\$DB_PORT dbname=\$DB_NAME user=\$DB_USER sslmode=require\"
  -c \"select id,name,loan_amount,months_time,status,created_at from loan_applications;\" -c \"select version();\""'
# -> 1 строка, status APPROVED; PostgreSQL 16.15 (...yandex...)

# сохранность данных после перезапуска приложения
ssh -i ~/.ssh/id_ed25519 yc-user@$IP 'sudo systemctl restart microloan-gateway; sleep 40;
  curl -s localhost:8081/api/v1/loans/d1fee28d-0190-41f9-99c5-d3f67246e3cb'
# -> та же заявка, status APPROVED
```

DNS-имя БД резолвится только во внутренний адрес `10.131.0.20` — из интернета БД недоступна.

## Итог

Приложение доступно по адресу **http://153.51.115.77:8081** (API `/api/v1/loans`, health `/actuator/health`).
Публичный IP динамический: после остановки/запуска ВМ он может смениться (`yc compute instance get microloan-vm`).

Экономия до защиты: `yc compute instance stop microloan-vm` и `yc managed-postgresql cluster stop microloan-pg`
(запуск — `start`); оплачиваются в основном диски.
