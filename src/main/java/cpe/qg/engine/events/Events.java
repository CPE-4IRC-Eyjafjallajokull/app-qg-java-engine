package cpe.qg.engine.events;

/** Central registry of RabbitMQ events used by the engine. */
public enum Events {
  ASSIGNMENT_REQUEST("assignment_request"),
  ASSIGNMENT_PROPOSAL("assignment_proposal");

  private final String key;

  Events(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
