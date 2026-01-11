package cpe.qg.engine.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import cpe.qg.engine.decision.api.DecisionEngine;
import cpe.qg.engine.decision.model.DecisionResult;
import cpe.qg.engine.decision.model.VehicleAssignmentProposal;
import cpe.qg.engine.events.EventMessage;
import cpe.qg.engine.events.Events;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.MessageBrokerClient;
import cpe.qg.engine.messaging.Queues;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/** Sample handler for new incident events that emits an acknowledgement. */
public class IncidentHandler implements EventHandler {

  private final MessageBrokerClient brokerClient;
  private final boolean durableQueue;
  private final DecisionEngine decisionEngine;
  private final ObjectMapper objectMapper;
  private final Logger log = LoggerProvider.getLogger(IncidentHandler.class);
  private final AtomicBoolean apiQueueDeclared = new AtomicBoolean(false);

  public IncidentHandler(
      MessageBrokerClient brokerClient, boolean durableQueue, DecisionEngine decisionEngine) {
    this.brokerClient = brokerClient;
    this.durableQueue = durableQueue;
    this.decisionEngine = decisionEngine;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  }

  @Override
  public String eventKey() {
    return Events.NEW_INCIDENT.key();
  }

  @Override
  public void handle(EventMessage message) {
    ensureApiQueue();
    log.info("Processing new incident message: {}", message.rawPayload());
    UUID incidentId = extractIncidentId(message);
    if (incidentId != null && decisionEngine != null) {
      DecisionResult result = decisionEngine.proposeAssignments(incidentId);
      logDecisionResult(incidentId, result);
      publishDecisionProposal(incidentId, result);
    } else if (incidentId == null) {
      log.warn("Unable to extract incident_id from event payload");
    } else {
      log.warn("No decision engine configured, skipping assignment proposal");
    }
  }

  private void ensureApiQueue() {
    if (apiQueueDeclared.compareAndSet(false, true)) {
      brokerClient.declareQueue(Queues.SDMIS_API.queue(), durableQueue);
    }
  }

  private void publishDecisionProposal(UUID incidentId, DecisionResult result) {
    if (result == null) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("proposal_id", UUID.randomUUID().toString());
    payload.put("incident_id", incidentId.toString());
    payload.put("generated_at", Instant.now().toString());
    payload.put("proposals", proposalPayload(result.proposals()));
    payload.put("missing_by_vehicle_type", missingPayload(result.missingByVehicleType()));

    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("event", Events.VEHICLE_ASSIGNMENT_PROPOSAL.key());
    envelope.put("payload", payload);

    try {
      String message = objectMapper.writeValueAsString(envelope);
      brokerClient.publish(Queues.SDMIS_API.queue(), message);
      log.info(
          "Sent decision proposal to {} for incident {}", Queues.SDMIS_API.queue(), incidentId);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize decision proposal for incident {}", incidentId, e);
    }
  }

  private List<Map<String, Object>> proposalPayload(List<VehicleAssignmentProposal> proposals) {
    if (proposals == null || proposals.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> payload = new ArrayList<>();
    for (VehicleAssignmentProposal proposal : proposals) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("incident_phase_id", proposal.incidentPhaseId());
      item.put("vehicle_id", proposal.vehicleId());
      item.put("distance_km", proposal.distanceKm());
      item.put("estimated_time_min", proposal.estimatedTimeMin());
      item.put("route_geometry", proposal.routeGeometry());
      item.put("energy_level", proposal.energyLevel());
      item.put("score", proposal.score());
      item.put("rationale", proposal.rationale());
      payload.add(item);
    }
    return payload;
  }

  private Map<String, Integer> missingPayload(Map<UUID, Integer> missingByType) {
    if (missingByType == null || missingByType.isEmpty()) {
      return Map.of();
    }
    Map<String, Integer> payload = new LinkedHashMap<>();
    for (Map.Entry<UUID, Integer> entry : missingByType.entrySet()) {
      payload.put(entry.getKey().toString(), entry.getValue());
    }
    return payload;
  }

  private UUID extractIncidentId(EventMessage message) {
    if (message == null || message.body() == null) {
      return null;
    }
    JsonNode payloadNode = message.body().get("payload");
    if (payloadNode == null) {
      log.warn("Missing payload in event message");
      return null;
    }
    JsonNode idNode = payloadNode.get("incident_id");
    if (idNode == null || !idNode.isTextual()) {
      log.warn("Missing or invalid incident_id in payload");
      return null;
    }
    try {
      return UUID.fromString(idNode.asText());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid incident_id format: {}", idNode.asText());
      return null;
    }
  }

  private void logDecisionResult(UUID incidentId, DecisionResult result) {
    if (result == null) {
      log.info("No decision result produced for incident {}", incidentId);
      return;
    }
    log.info(
        "Decision proposals generated for incident {}: {} proposal(s), {} missing types",
        incidentId,
        result.proposals() == null ? 0 : result.proposals().size(),
        result.missingByVehicleType() == null ? 0 : result.missingByVehicleType().size());
    if (result.proposals() != null && !result.proposals().isEmpty()) {
      log.debug("Decision proposals: {}", result.proposals());
    }
    if (result.missingByVehicleType() != null && !result.missingByVehicleType().isEmpty()) {
      log.warn(
          "Missing vehicle types for incident {}: {}", incidentId, result.missingByVehicleType());
    }
  }
}
