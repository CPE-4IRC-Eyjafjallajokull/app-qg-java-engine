package cpe.qg.engine.service;

import java.util.Objects;

/**
 * Value object that wraps the consumed payload with its source queue.
 */
public record IncomingMessage(String queue, String payload) {

    public IncomingMessage {
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("Queue name is required");
        }
        Objects.requireNonNull(payload, "Payload is required");
    }
}
