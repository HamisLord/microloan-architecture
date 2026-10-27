# КТ-14. Развёртывание полноценного приложения в облаке — план презентации и подготовка к защите

Приложение: **microloan-archetype** (Java 21, Spring Boot 3.5, Maven).
Адрес: **http://153.51.115.77:8081** · подробный журнал команд — `DEPLOY.md`.

---

## Структура презентации (10–12 слайдов)

### Слайд 1. Титул
КТ-14 «Развёртывание полноценного приложения в облаке», ФИО, группа, дата защиты.

### Слайд 2. Цель работы
- Развернуть собственное Java веб-приложение в Yandex Cloud.
- Вынести БД в управляемый сервис Yandex Managed Service for PostgreSQL.
- Настроить сетевую безопасность и автозапуск, проверить работу и сохранность данных.

### Слайд 3. Приложение
- Сервис микрозаймов из двух микросервисов:
  - **loan-gateway** — REST API (`POST /api/v1/loans`, `GET /api/v1/loans/{id}`), валидация, хранение заявок в PostgreSQL (JPA/Hibernate);
  - **scoring-service** — получает заявку из Kafka, принимает решение и возвращает результат.
- Асинхронный обмен через Apache Kafka (топики `new-applications`, `scoring-results`); событие отправляется только после коммита транзакции.
- Стек: Java 21, Spring Boot 3.5, Spring Data JPA, Spring Kafka, Actuator/Micrometer, Maven, Lombok.

### Слайд 4. Схема архитектуры

```
                       Интернет
                          │  TCP 22 (SSH), TCP 8081 (HTTP API)
                          ▼
     ┌──────────── Yandex Cloud, folder b1gui733edqs29khkqt3 ─────────────┐
     │  VPC «default», подсеть default-ru-central1-e (10.131.0.0/24)      │
     │                                                                    │
     │  ┌─ SG microloan-vm-sg ─────────────────────┐                      │
     │  │ ВМ microloan-vm (Ubuntu 24.04, 2vCPU 20%,│                      │
     │  │ 4 ГБ, HDD 20 ГБ) 10.131.0.34 / 153.51.115.77                    │
     │  │  systemd: microloan-gateway  :8081 ──────┼──┐                   │
     │  │  systemd: microloan-scoring  :8082 (lo)  │  │ TCP 6432, TLS     │
     │  │  systemd+Docker: Kafka 127.0.0.1:9092    │  │                   │
     │  └──────────────────────────────────────────┘  ▼                   │
     │                         ┌─ SG microloan-db-sg (6432 только от vm-sg)┐
     │                         │ Managed PostgreSQL 16 «microloan-pg»     │
     │                         │ b2.medium, SSD 10 ГБ, без публичного IP  │
     │                         │ база loan_db, пользователь loan_admin    │
     │                         └──────────────────────────────────────────┘
     └────────────────────────────────────────────────────────────────────┘
```
Поток заявки: клиент → gateway (сохранение, статус CREATED) → Kafka → scoring → Kafka → gateway (APPROVED/REJECTED в БД).

### Слайд 5. Используемые сервисы Yandex Cloud

| Сервис | Ресурс | Назначение |
|--------|--------|-----------|
| Compute Cloud | ВМ `microloan-vm`, standard-v3, 2 vCPU (20%), 4 ГБ, network-hdd 20 ГБ, Ubuntu 24.04 LTS | Запуск JVM-сервисов и Kafka |
| Managed Service for PostgreSQL | `microloan-pg`, PG 16, b2.medium (2 vCPU 50%, 4 ГБ), network-ssd 10 ГБ, 1 хост | Управляемая БД: бэкапы, обновления, мониторинг |
| Virtual Private Cloud | сеть `default`, подсеть `default-ru-central1-e`, публичный IP, 2 группы безопасности | Сеть и межсетевой экран |
| Resource Manager / Quota Manager | каталог, квоты | Диагностика ограничений |
| CLI `yc` | профиль default | Всё создание — командами, без консоли |

### Слайд 6. Настройки безопасности
- `microloan-vm-sg`: вход TCP 22 и 8081 с 0.0.0.0/0, исход — любой. Порт 8082 и Kafka снаружи закрыты.
- `microloan-db-sg`: вход TCP 6432 **только** от группы `microloan-vm-sg` (ссылка на группу, а не CIDR).
- У кластера БД нет публичного IP; DNS-имя резолвится во внутренний адрес. Подключение по TLS (`sslmode=require`).
- SSH только по ключу (ed25519), пользователь `yc-user` создан через cloud-init.
- Пароль БД не хранится в коде: в `application.yml` — `${DB_PASSWORD}`; локально `.env` (в `.gitignore`),
  на ВМ — `/etc/microloan/gateway.env` с правами 600 (root), подключается через `EnvironmentFile`.
- Приложения работают от системного пользователя `microloan` без shell.

### Слайд 7. Подготовка приложения к облаку
- Параметры подключения → переменные окружения `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSLMODE`,
  а также `KAFKA_BOOTSTRAP_SERVERS`, `SERVER_PORT`, `DB_POOL_SIZE` (значения по умолчанию — для локального запуска).
- `spring.config.import: optional:file:.env[.properties]` — локальный запуск не сломан.
- `docker-compose.yml` и CI тоже берут пароль из окружения.
- Сборка: `mvn -DskipTests package` → два fat-jar (≈80 и 42 МБ).

### Слайд 8. Этапы развёртывания
1. Изучение проекта, вынос настроек, сборка jar.
2. Группы безопасности (`yc vpc security-group create`).
3. Кластер PostgreSQL с базой и пользователем (`yc managed-postgresql cluster create`, ~10 мин).
4. ВМ с cloud-init: Java 21, Docker, psql (`yc compute instance create`).
5. Загрузка jar и unit-файлов (`scp`), env-файл с паролем через stdin (`ssh`).
6. Запуск `kafka-container`, `microloan-scoring`, `microloan-gateway` через `systemctl enable --now`.
7. Проверка: health, POST/GET заявки, `psql` в БД, перезапуск сервиса.

### Слайд 9. Результат
- `GET /actuator/health` → `UP`, компонент `db` → PostgreSQL `UP`.
- `POST /api/v1/loans` → 201, статус `CREATED`; через ~1 с `GET` → `APPROVED`.
- `select ... from loan_applications` в Managed PostgreSQL показывает заявку; после рестарта сервиса данные на месте.
- Сервисы в автозапуске, при падении перезапускаются systemd.

### Слайд 10. Проблемы и решения

| Проблема | Решение |
|----------|---------|
| Квота `vpc.networks.count` = 2, своя сеть не создаётся | Использована существующая сеть `default` и её подсеть, изоляция — группами безопасности |
| `b2.medium` для PostgreSQL доступен только в зонах d и e | Выбрана зона ru-central1-e |
| ВМ не создавалась: в e исчерпаны квоты HDD/SSD и vCPU (лимит 2 vCPU на зону), зона b недоступна, в a «Resource allocation is restricted», в d нет vCPU | Диагностика `yc quota-manager quota-limit list`; удалены неиспользуемые ВМ прошлых работ (в т.ч. группа ВМ целиком — её узел иначе пересоздаётся) |
| Kafka нужна приложению, а Managed Kafka дорогая | Kafka в Docker на той же ВМ, слушает только localhost, heap ограничен 512 МБ |
| Пароль был захардкожен в `application.yml` и `docker-compose.yml` | Переменные окружения, `.env` в `.gitignore`, env-файл 600 на ВМ |
| Мало памяти (4 ГБ) на 2 JVM + Kafka | Ограничения `-Xmx384m`/`-Xmx256m`/`512m`, пул соединений 10 |

### Слайд 11. Выводы
- Управляемая БД снимает задачи администрирования (бэкапы, обновления, отказоустойчивость при добавлении хостов).
- Группы безопасности позволяют закрыть БД от интернета и ограничить доступ ссылкой на группу ВМ.
- Конфигурация через переменные окружения (12-factor) позволяет один и тот же jar запускать локально и в облаке.
- Квоты облака надо проверять до начала работ; CLI `yc` даёт воспроизводимость (все команды — в `DEPLOY.md`).
- Развитие: Container Registry + Docker/COG, Managed Kafka, Application Load Balancer + HTTPS, статический IP,
  Lockbox для секретов, Terraform, 2-й хост БД в другой зоне.

### Слайд 12. Спасибо / вопросы
Ссылка на репозиторий, адрес приложения.

---

## Список скриншотов, которые нужно сделать

1. Консоль Yandex Cloud → каталог → список ресурсов (ВМ `microloan-vm`, кластер `microloan-pg`).
2. Compute Cloud → `microloan-vm` → «Обзор» (платформа, vCPU 20%, 4 ГБ, диск, публичный IP, группа безопасности).
3. Managed Service for PostgreSQL → `microloan-pg` → «Обзор» (версия 16, b2.medium, SSD 10 ГБ, статус Alive).
4. `microloan-pg` → вкладки «Хосты» (без публичного доступа), «Базы данных» (`loan_db`), «Пользователи» (`loan_admin`).
5. `microloan-pg` → «Мониторинг» (графики соединений/запросов после тестовых заявок).
6. VPC → «Группы безопасности» → правила `microloan-vm-sg` и `microloan-db-sg`.
7. Терминал: `yc compute instance list` и `yc managed-postgresql cluster list`.
8. Терминал (SSH на ВМ): `systemctl status microloan-gateway microloan-scoring kafka-container`.
9. Терминал: `sudo journalctl -u microloan-gateway -n 30` — строки `HikariPool-1 - Start completed`, `Tomcat started on port 8081`.
10. Браузер: `http://153.51.115.77:8081/actuator/health` (UP, db: PostgreSQL).
11. Терминал/Postman: `POST /api/v1/loans` → 201 `CREATED`, затем `GET /api/v1/loans/{id}` → `APPROVED`.
12. Терминал (SSH): `psql ... -c "select * from loan_applications;"` — строки в Managed PostgreSQL.
13. Фрагмент `application.yml` с `${DB_PASSWORD}` и `.gitignore` с `.env` (показать, что пароля в коде нет).
14. (Опционально) `curl -m 5 http://153.51.115.77:8082` — таймаут: закрытый порт.

---

## Возможные вопросы на защите и короткие ответы

1. **Почему Managed PostgreSQL, а не PostgreSQL на той же ВМ?**
   Облако берёт на себя резервное копирование, обновления, мониторинг, отказоустойчивость (можно добавить реплику); данные не зависят от ВМ.

2. **Почему порт 6432, а не 5432?**
   6432 — порт пулера соединений Odyssey, который Yandex ставит перед PostgreSQL; он экономит соединения. 5432 — прямое подключение к PostgreSQL.

3. **Как ограничен доступ к БД?**
   У хоста нет публичного IP, а группа `microloan-db-sg` пускает TCP 6432 только от ресурсов с группой `microloan-vm-sg`. Плюс логин/пароль и TLS.

4. **Где хранится пароль БД и почему его нет в git?**
   В коде — плейсхолдер `${DB_PASSWORD}`. Локально — `.env` (в `.gitignore`), на ВМ — `/etc/microloan/gateway.env` с правами 600, systemd читает его через `EnvironmentFile`. Лучшая практика — Yandex Lockbox.

5. **Что такое группа безопасности и чем отличается от firewall на ВМ?**
   Это stateful-правила на уровне виртуального сетевого интерфейса в VPC; работают до ОС, задаются через API, могут ссылаться на другие группы.

6. **Как приложение запускается и что будет после перезагрузки ВМ?**
   Через systemd-юниты с `enable` (автозапуск) и `Restart=on-failure`; Kafka — юнит, запускающий Docker-контейнер с `Restart=always`.

7. **Зачем Kafka и почему не Managed Kafka?**
   Сервисы общаются асинхронно: gateway публикует заявку, scoring отвечает. Managed Kafka для учебной работы дорог, поэтому один брокер в Docker на ВМ, доступный только с localhost.

8. **Что делает cloud-init?**
   При первом старте ВМ создаёт пользователя с SSH-ключом, ставит пакеты (Java 21, Docker, psql), создаёт системного пользователя и каталоги.

9. **Как проверили, что данные сохраняются в БД?**
   POST создал заявку, `psql` с ВМ показал строку в `loan_applications` Managed PostgreSQL; после рестарта сервиса GET вернул ту же запись.

10. **Какие проблемы возникли при развёртывании?**
    Квоты: лимит 2 сети и 2 vCPU на зону, исчерпанные диски в зоне e, недоступная зона b, ограничение в зоне a. Решение — диагностика квот и удаление неиспользуемых ресурсов прошлых работ.

11. **Почему зона ru-central1-e?**
    Класс `b2.medium` для PostgreSQL доступен только в d и e; ВМ в той же зоне — меньше задержка и нет межзонального трафика.

12. **Что такое core fraction 20% и b2.medium?**
    Burstable-ресурсы: гарантируется доля vCPU (20% у ВМ, 50% у b2.medium), что дешевле; для учебной нагрузки достаточно.

13. **Сколько это стоит и как экономить?**
    ~5–6 ₽/ч (БД ~3–4, ВМ ~1.5–2, IP ~0.3). Можно останавливать ВМ и кластер (`stop`) — платятся только диски; после защиты всё удаляется.

14. **Как обеспечить HTTPS и постоянный адрес?**
    Зарезервировать статический IP, поставить Application Load Balancer или nginx с сертификатом из Certificate Manager, закрыть 8081 снаружи.

15. **Как бы вы автоматизировали развёртывание?**
    Terraform для инфраструктуры, Docker-образы в Container Registry, CI/CD (GitHub Actions) для сборки и деплоя, секреты — в Lockbox.
