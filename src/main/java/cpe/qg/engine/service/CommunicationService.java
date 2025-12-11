package cpe.qg.engine.service;

import cpe.qg.engine.config.RabbitConfig;
import cpe.qg.engine.database.DatabaseClient;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.MessageBrokerClient;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Example of wiring the RabbitMQ and PostgreSQL connectors together.
 */
public class CommunicationService {

    private final MessageBrokerClient brokerClient;
    private final DatabaseClient databaseClient;
    private final RabbitConfig rabbitConfig;
    private final Logger log = LoggerProvider.getLogger(CommunicationService.class);

    public CommunicationService(MessageBrokerClient brokerClient, DatabaseClient databaseClient, RabbitConfig rabbitConfig) {
        this.brokerClient = brokerClient;
        this.databaseClient = databaseClient;
        this.rabbitConfig = rabbitConfig;
    }

    public void start(MessageHandler handler) {
        databaseClient.connect();
        brokerClient.connect();
        brokerClient.declareQueue(rabbitConfig.queueName(), rabbitConfig.durableQueue());

        DeliverCallback callback = (tag, delivery) -> {
            String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try {
                handler.handle(payload);
            } catch (Exception e) {
                log.error("Error while processing message: {}", payload, e);
            }
        };

        brokerClient.consume(rabbitConfig.queueName(), callback);
        log.info("Listening for messages on queue {}", rabbitConfig.queueName());
    }

    public void startPersistingMessages() {
        start(this::persistMessage);
    }

    private void persistMessage(String payload) {
        try (Connection connection = databaseClient.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO event_log(message) VALUES (?)")) {
            statement.setString(1, payload);
            statement.executeUpdate();
            log.info("Message stored in database");
        } catch (SQLException e) {
            log.error("Failed to persist message", e);
        }
    }

    public interface MessageHandler {
        void handle(String payload) throws Exception;
    }
}
