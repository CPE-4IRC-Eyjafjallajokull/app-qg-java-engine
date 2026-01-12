# QG Java Engine

Lightweight Java 21 engine that connects to RabbitMQ and PostgreSQL, loads configuration from a `.env` file, and listens to a single RabbitMQ queue while routing messages by their `event` key.

## Prerequisites
- Java 21+
- Maven 3.9+
- RabbitMQ and PostgreSQL reachable from the app (or the docker-compose services)

## Configuration
Copy `.env.example` to `.env` and set the values:

- `LOG_LEVEL` (e.g., `INFO`, `DEBUG`)
- `POSTGRES_URL` (jdbc url)  
  `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_POOL_SIZE`, `POSTGRES_CONNECTION_TIMEOUT_MS`
- `RABBITMQ_URI` (amqp uri)  
  `RABBITMQ_QUEUE_DURABLE`
- `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` (for SDMIS API access)
- `SDMIS_API_BASE_URL`, `SDMIS_API_TIMEOUT_MS` (defaults to `http://localhost:3001`)
- `DECISION_MAX_DISTANCE_KM`, `DECISION_MIN_ENERGY_LEVEL` (optional decision filters)

Queues and their direction (SUB/PUB) are defined in `src/main/java/cpe/qg/engine/events/Queues.java`.

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
The `App` entrypoint runs connectivity checks against RabbitMQ and PostgreSQL, then listens to the queues defined in `Queues` and dispatches each message based on its `event` field. Clean shutdown on `Ctrl+C` closes RabbitMQ and PostgreSQL resources.

## Add event handlers
Messages consumed from the subscribed queues must contain an `event` field, e.g.:
```json
{"event":"assignment_request","payload":{...}}
```
To react to new events:
1. Declare the event key in `Events` (see `src/main/java/cpe/qg/engine/events/Events.java`).
2. Implement `EventHandler` with the matching `eventKey()` and business logic:
```java
class CustomHandler implements EventHandler {
    private final MessageBrokerClient broker;
    private final boolean durable;
    CustomHandler(MessageBrokerClient broker, boolean durable) { this.broker = broker; this.durable = durable; }

    @Override
    public String eventKey() { return Events.ASSIGNMENT_REQUEST.key(); }

    @Override
    public void handle(EventMessage message) {
        broker.declareQueue(Events.INCIDENT_ACK.key(), durable);
        broker.publish(Events.INCIDENT_ACK.key(), "{\"status\":\"received\"}");
    }
}
```
3. Register the handler in `App.buildHandlers(...)`:
```java
private static List<EventHandler> buildHandlers(RabbitConfig config, RabbitMqClient broker) {
    return List.of(new CustomHandler(broker, config.durableQueue()));
}
```
The app listens to all queues marked as `SUB` in `Queues` and dispatches every message to the handler matching the `event` key.

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
- `src/main/java/cpe/qg/engine/events` – events, queues, handlers, and listener
- `src/main/java/cpe/qg/engine/service` – connectivity probe
