package cpe.qg.engine.messaging;

import com.rabbitmq.client.DeliverCallback;
import cpe.qg.engine.events.EventDispatcher;
import cpe.qg.engine.events.EventMessage;
import cpe.qg.engine.events.EventPayloadParser;
import cpe.qg.engine.logging.LoggerProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/** Consumes queues and routes messages to an {@link EventDispatcher}. */
public class QueueListener implements AutoCloseable {

  private final MessageBrokerClient brokerClient;
  private final List<String> queueNames;
  private final boolean durableQueue;
  private final EventDispatcher dispatcher;
  private final EventPayloadParser parser;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final Logger log = LoggerProvider.getLogger(QueueListener.class);

  public QueueListener(
      MessageBrokerClient brokerClient,
      List<String> queueNames,
      boolean durableQueue,
      EventDispatcher dispatcher,
      EventPayloadParser parser) {
    this.brokerClient = Objects.requireNonNull(brokerClient, "Message broker client is required");
    this.queueNames = List.copyOf(Objects.requireNonNull(queueNames, "Queue names are required"));
    if (this.queueNames.isEmpty()) {
      throw new IllegalArgumentException("At least one queue is required");
    }
    this.durableQueue = durableQueue;
    this.dispatcher = Objects.requireNonNull(dispatcher, "Event dispatcher is required");
    this.parser = Objects.requireNonNull(parser, "Event payload parser is required");
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    brokerClient.connect();
    for (String queue : queueNames) {
      brokerClient.declareQueue(queue, durableQueue);
      brokerClient.consume(queue, callback(queue));
      log.info("Listening to queue '{}'", queue);
    }
  }

  private DeliverCallback callback(String queueName) {
    return (tag, delivery) -> {
      String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);
      try {
        EventMessage message = parser.parse(payload);
        dispatcher.dispatch(message);
      } catch (Exception e) {
        log.error("Discarding invalid message from {}: {}", queueName, payload, e);
      }
    };
  }

  @Override
  public void close() {
    if (!started.get()) {
      return;
    }
    brokerClient.close();
    log.info("Queue listener stopped");
  }
}
