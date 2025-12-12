package cpe.qg.engine.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses raw RabbitMQ payloads into an {@link EventMessage}.
 */
public class EventPayloadParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventMessage parse(String payload) {
        try {
            JsonNode body = objectMapper.readTree(payload);
            JsonNode eventNode = body.get("event");
            if (eventNode == null || !eventNode.isTextual() || eventNode.asText().isBlank()) {
                throw new IllegalArgumentException("Payload does not contain an 'event' field");
            }
            return new EventMessage(eventNode.asText(), body, payload);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid message payload", e);
        }
    }
}
