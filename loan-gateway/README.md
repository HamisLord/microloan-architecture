# ⚙️ Microloan API Gateway
https://t.me/Java_mechanics

Микросервисный шлюз для обработки кредитных заявок. Проект демонстрирует современную backend-архитектуру с использованием асинхронного обмена сообщениями и централизованного мониторинга.

## 🛠 Стек технологий
* **Java 21** / **Spring Boot 3.x**
* **PostgreSQL** + **Hibernate** (Spring Data JPA)
* **Apache Kafka** (Асинхронная передача заявок)
* **Docker & Docker Compose** (Контейнеризация инфраструктуры)
* **Prometheus & Grafana** (Сбор метрик и мониторинг JVM)

## 🚀 Как запустить локально

1. Клонируйте репозиторий:
   `git clone https://github.com/HamisLord/microloan-architecture.git`
2. Поднимите инфраструктуру (БД, брокер, мониторинг) через Docker:
   `docker-compose up -d`
3. Запустите приложение `LoanGatewayApplication` в вашей IDE.

## 📡 API Endpoints (Пример запроса)
**POST** `/api/v1/loans`

```json
{
  "name": "Иван Иванов",
  "passport": "1234567890",
  "email": "ivan@example.com",
  "loanAmount": 250000.00,
  "monthsTime": 12
}
