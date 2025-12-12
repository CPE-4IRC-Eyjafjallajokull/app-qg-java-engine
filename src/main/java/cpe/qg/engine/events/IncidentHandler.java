package cpe.qg.engine.events;

import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.MessageBrokerClient;
import cpe.qg.engine.messaging.Queues;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sample handler for new incident events that emits an acknowledgement.
 */
public class IncidentHandler implements EventHandler {

    private final MessageBrokerClient brokerClient;
    private final boolean durableQueue;
    private final Logger log = LoggerProvider.getLogger(IncidentHandler.class);
    private final AtomicBoolean ackQueueDeclared = new AtomicBoolean(false);

    public IncidentHandler(MessageBrokerClient brokerClient, boolean durableQueue) {
        this.brokerClient = brokerClient;
        this.durableQueue = durableQueue;
    }

    @Override
    public String eventKey() {
        return Events.NEW_INCIDENT.key();
    }

    @Override
    public void handle(EventMessage message) {
        ensureAckQueue();
        log.info("Processing new incident message: {}", message.rawPayload());
        String ackPayload = acknowledgementPayload(message.rawPayload());
        brokerClient.publish(Queues.SDMIS_API.queue(), ackPayload);
        log.info("Sent incident acknowledgement to {}: {}", Queues.SDMIS_API.queue(), ackPayload);
    }

    private void ensureAckQueue() {
        if (ackQueueDeclared.compareAndSet(false, true)) {
            brokerClient.declareQueue(Queues.SDMIS_API.queue(), durableQueue);
        }
    }

    private String acknowledgementPayload(String incomingPayload) {
        return """
                {"event":"%s","receivedAt":"%s","originalPayload":%s}
                """.formatted(Events.INCIDENT_ACK.key(), Instant.now(), wrapPayload(incomingPayload));
    }

    private String wrapPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return "\"\"";
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        return "\"%s\"".formatted(trimmed.replace("\"", "\\\""));
    }
}
