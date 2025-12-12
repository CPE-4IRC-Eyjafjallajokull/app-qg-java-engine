package cpe.qg.engine.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Envelope describing an incoming message with its event key.
 */
public record EventMessage(String eventKey, JsonNode body, String rawPayload) {

    public EventMessage {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("Event key is required");
        }
        Objects.requireNonNull(body, "Event body is required");
        Objects.requireNonNull(rawPayload, "Raw payload is required");
    }
}
