package cpe.qg.engine.config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable RabbitMQ settings loaded from the environment.
 */
public record RabbitConfig(String uri, List<String> queues, boolean durableQueue) {

    public RabbitConfig {
        Objects.requireNonNull(uri, "RabbitMQ URI is required");
        Objects.requireNonNull(queues, "RabbitMQ queues are required");

        List<String> normalized = queues.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(q -> !q.isEmpty())
                .distinct()
                .toList();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one RabbitMQ queue is required");
        }

        queues = Collections.unmodifiableList(normalized);
    }

    public String primaryQueue() {
        return queues.get(0);
    }
}
