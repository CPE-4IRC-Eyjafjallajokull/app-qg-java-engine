package cpe.qg.engine.events;

import cpe.qg.engine.handlers.EventHandler;
import cpe.qg.engine.logging.LoggerProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/** Dispatches an event to the matching handler. */
public class EventDispatcher {

  private final Map<String, EventHandler> handlers = new HashMap<>();
  private final Logger log = LoggerProvider.getLogger(EventDispatcher.class);

  public EventDispatcher(List<EventHandler> handlers) {
    Objects.requireNonNull(handlers, "Handlers are required");
    for (EventHandler handler : handlers) {
      Objects.requireNonNull(handler, "Handler instance is required");
      String eventKey = handler.eventKey();
      if (eventKey == null || eventKey.isBlank()) {
        throw new IllegalArgumentException("Handler provided an empty event key");
      }
      if (this.handlers.containsKey(eventKey)) {
        throw new IllegalArgumentException(
            "Duplicate handler registration for event '%s'".formatted(eventKey));
      }
      this.handlers.put(eventKey, handler);
    }
  }

  public void dispatch(EventMessage message) {
    EventHandler handler = handlers.get(message.eventKey());
    if (handler == null) {
      log.warn("No handler registered for event '{}'", message.eventKey());
      return;
    }
    try {
      handler.handle(message);
    } catch (Exception e) {
      log.error(
          "Handler for event '{}' failed on payload {}",
          message.eventKey(),
          message.rawPayload(),
          e);
    }
  }
}
