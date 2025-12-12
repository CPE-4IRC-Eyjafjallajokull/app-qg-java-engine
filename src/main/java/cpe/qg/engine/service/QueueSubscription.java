package cpe.qg.engine.service;

import java.util.Objects;

/**
 * Declaration of how the application wants to handle a specific queue.
 */
public record QueueSubscription(String queueName, MessageHandler handler) {

    public QueueSubscription {
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("Queue name is required");
        }
        Objects.requireNonNull(handler, "Message handler is required");
    }
}
