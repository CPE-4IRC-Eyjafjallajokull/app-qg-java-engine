package cpe.qg.engine.handlers;

import cpe.qg.engine.events.EventMessage;

/** Handles a specific event key coming from the shared RabbitMQ queue. */
public interface EventHandler {

  /**
   * @return the event key this handler responds to
   */
  String eventKey();

  void handle(EventMessage message) throws Exception;
}
