package cpe.qg.engine.service;

import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.MessageBrokerClient;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates subscription to multiple queues and forwards payloads to handlers.
 */
public class QueueConsumerService implements AutoCloseable {

    private final MessageBrokerClient brokerClient;
    private final List<QueueSubscription> subscriptions;
    private final boolean durableQueues;
    private final Logger log = LoggerProvider.getLogger(QueueConsumerService.class);
    private final AtomicBoolean started = new AtomicBoolean(false);

    public QueueConsumerService(MessageBrokerClient brokerClient, List<QueueSubscription> subscriptions, boolean durableQueues) {
        this.brokerClient = Objects.requireNonNull(brokerClient, "Message broker client is required");
        List<QueueSubscription> normalized = List.copyOf(Objects.requireNonNull(subscriptions, "Queue subscriptions are required"));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one queue subscription is required");
        }
        this.subscriptions = normalized;
        this.durableQueues = durableQueues;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        brokerClient.connect();
        for (QueueSubscription subscription : subscriptions) {
            brokerClient.declareQueue(subscription.queueName(), durableQueues);
            brokerClient.consume(subscription.queueName(), buildCallback(subscription));
            log.info("Subscribed to queue {}", subscription.queueName());
        }
    }

    private DeliverCallback buildCallback(QueueSubscription subscription) {
        return (tag, delivery) -> {
            String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);
            IncomingMessage message = new IncomingMessage(subscription.queueName(), payload);
            try {
                subscription.handler().handle(message);
            } catch (Exception e) {
                log.error("Failed to handle message from {}: {}", subscription.queueName(), payload, e);
            }
        };
    }

    @Override
    public void close() {
        if (!started.get()) {
            return;
        }
        brokerClient.close();
        log.info("Queue consumer service stopped");
    }
}
