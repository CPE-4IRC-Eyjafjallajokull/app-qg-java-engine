package cpe.qg.engine.events;

/** Central registry of RabbitMQ events used by the engine. */
public enum Events {
  NEW_INCIDENT("new_incident"),
  INCIDENT_ACK("incident_ack"),
  VEHICLE_ASSIGNMENT_PROPOSAL("vehicle_assignment_proposal");

  private final String key;

  Events(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
