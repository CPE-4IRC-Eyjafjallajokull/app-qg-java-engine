package cpe.qg.engine.messaging;

import cpe.qg.engine.config.RabbitConfig;
import cpe.qg.engine.logging.LoggerProvider;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.MessageProperties;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ connector that exposes minimal operations for publishing and consuming.
 */
public class RabbitMqClient implements MessageBrokerClient {

    private final RabbitConfig config;
    private final Logger log = LoggerProvider.getLogger(RabbitMqClient.class);

    private Connection connection;
    private Channel channel;

    public RabbitMqClient(RabbitConfig config) {
        this.config = config;
    }

    @Override
    public void connect() {
        if (channel != null && channel.isOpen()) {
            return;
        }

        ConnectionFactory factory = new ConnectionFactory();
        try {
            factory.setUri(config.uri());
            factory.setAutomaticRecoveryEnabled(true);
            factory.setTopologyRecoveryEnabled(true);
            connection = factory.newConnection("qg-engine");
            channel = connection.createChannel();
            log.info("Connected to RabbitMQ {}", config.uri());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to connect to RabbitMQ", e);
        }
    }

    @Override
    public void declareQueue(String queueName, boolean durable) {
        ensureConnected();
        try {
            channel.queueDeclare(queueName, durable, false, false, null);
            log.info("Ensured queue exists: {}", queueName);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to declare queue " + queueName, e);
        }
    }

    @Override
    public void publish(String queueName, String message) {
        ensureConnected();
        try {
            channel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes(StandardCharsets.UTF_8));
            log.debug("Published message to queue {} ({} bytes)", queueName, message.length());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to publish message to queue " + queueName, e);
        }
    }

    @Override
    public void consume(String queueName, DeliverCallback deliverCallback) {
        ensureConnected();
        try {
            channel.basicConsume(queueName, true, deliverCallback, tag -> {
            });
            log.info("Subscribed to queue {}", queueName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to subscribe to queue " + queueName, e);
        }
    }

    @Override
    public void healthCheck() {
        config.queues().forEach(queue -> declareQueue(queue, config.durableQueue()));
    }

    private void ensureConnected() {
        if (channel == null || !channel.isOpen()) {
            connect();
        }
    }

    @Override
    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException | TimeoutException e) {
            log.warn("Error while closing RabbitMQ channel", e);
        }

        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            log.warn("Error while closing RabbitMQ connection", e);
        }
    }
}
