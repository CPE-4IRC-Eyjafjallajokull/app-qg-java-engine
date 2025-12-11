# QG Java Engine

Lightweight Java 17 engine that connects to RabbitMQ and PostgreSQL, loads configuration from a `.env` file, and exposes a simple connectivity probe as its entrypoint.

## Prerequisites
- Java 17+
- Maven 3.9+
- RabbitMQ and PostgreSQL reachable from the app (or the docker-compose services)

## Configuration
Copy `.env.example` to `.env` and set the values:

- `LOG_LEVEL` (e.g., `INFO`, `DEBUG`)
- `POSTGRES_URL` (jdbc url)  
  `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_POOL_SIZE`, `POSTGRES_CONNECTION_TIMEOUT_MS`
- `RABBITMQ_URI` (amqp uri)  
  `RABBITMQ_QUEUE`, `RABBITMQ_QUEUE_DURABLE`

Legacy DSN keys were removed; only the keys above are used.

## Build and test
```bash
mvn test
mvn package
```
The assembly plugin creates `target/app-qg-java-engine-1.0-SNAPSHOT-jar-with-dependencies.jar`.

## Run locally
```bash
java -jar target/app-qg-java-engine-1.0-SNAPSHOT-jar-with-dependencies.jar
```
The `App` entrypoint runs connectivity checks against RabbitMQ and PostgreSQL, then stays alive:
- Consumes messages from queue `test_event` and logs them.
- Publishes a sample JSON payload to queue `test_pub` on startup.
- Clean shutdown on `Ctrl+C` (closes RabbitMQ and PostgreSQL resources).

## Docker
Build and run with Docker:
```bash
docker build -t qg-engine .
docker run --env-file .env --network pt-net qg-engine
```

## Structure
- `src/main/java/cpe/qg/engine/config` – environment loading and typed configs
- `src/main/java/cpe/qg/engine/logging` – SLF4J/Logback setup
- `src/main/java/cpe/qg/engine/database` – PostgreSQL connector (HikariCP)
- `src/main/java/cpe/qg/engine/messaging` – RabbitMQ connector
- `src/main/java/cpe/qg/engine/service` – connectivity probe and message pipeline wiring
