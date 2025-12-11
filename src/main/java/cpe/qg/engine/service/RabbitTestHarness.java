package cpe.qg.engine.service;

import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.MessageBrokerClient;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal harness to validate RabbitMQ connectivity: consume from one queue and publish to another.
 */
public class RabbitTestHarness implements AutoCloseable {

    private final MessageBrokerClient brokerClient;
    private final String consumeQueue;
    private final String publishQueue;
    private final Logger log = LoggerProvider.getLogger(RabbitTestHarness.class);
    private final AtomicBoolean started = new AtomicBoolean(false);

    public RabbitTestHarness(MessageBrokerClient brokerClient, String consumeQueue, String publishQueue) {
        this.brokerClient = brokerClient;
        this.consumeQueue = consumeQueue;
        this.publishQueue = publishQueue;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        brokerClient.connect();
        brokerClient.declareQueue(consumeQueue, true);
        brokerClient.declareQueue(publishQueue, true);

        DeliverCallback callback = (tag, delivery) -> {
            String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);
            log.info("Received message on {}: {}", consumeQueue, payload);
        };
        brokerClient.consume(consumeQueue, callback);

        publishSample();
        log.info("RabbitMQ test harness running. Consuming '{}' and publishing to '{}'.", consumeQueue, publishQueue);
    }

    public void publishSample() {
        String payload = samplePayload();
        brokerClient.publish(publishQueue, payload);
        log.info("Published sample payload to {}: {}", publishQueue, payload);
    }

    private String samplePayload() {
        return "{\"source\":\"qg-java-engine\",\"event\":\"test_pub\",\"sentAt\":\"%s\"}"
                .formatted(Instant.now().toString());
    }

    @Override
    public void close() {
        if (started.get()) {
            brokerClient.close();
            log.info("RabbitMQ test harness stopped.");
        }
    }
}
