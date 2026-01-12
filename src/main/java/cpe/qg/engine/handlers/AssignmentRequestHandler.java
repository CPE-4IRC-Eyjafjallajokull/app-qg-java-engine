package cpe.qg.engine.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import cpe.qg.engine.decision.api.DecisionEngine;
import cpe.qg.engine.decision.model.AssignmentRequest;
import cpe.qg.engine.decision.model.DecisionResult;
import cpe.qg.engine.decision.model.MissingVehicle;
import cpe.qg.engine.decision.model.VehicleAssignmentProposal;
import cpe.qg.engine.decision.model.VehicleNeed;
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

/** Handles assignment request events and emits assignment proposals. */
public class AssignmentRequestHandler implements EventHandler {

  private final MessageBrokerClient brokerClient;
  private final boolean durableQueue;
  private final DecisionEngine decisionEngine;
  private final ObjectMapper objectMapper;
  private final Logger log = LoggerProvider.getLogger(AssignmentRequestHandler.class);
  private final AtomicBoolean apiQueueDeclared = new AtomicBoolean(false);

  public AssignmentRequestHandler(
      MessageBrokerClient brokerClient, boolean durableQueue, DecisionEngine decisionEngine) {
    this.brokerClient = brokerClient;
    this.durableQueue = durableQueue;
    this.decisionEngine = decisionEngine;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  }

  @Override
  public String eventKey() {
    return Events.ASSIGNMENT_REQUEST.key();
  }

  @Override
  public void handle(EventMessage message) {
    ensureApiQueue();
    log.info("Processing assignment request message: {}", message.rawPayload());
    AssignmentRequest request = extractRequest(message);
    if (request == null || request.incidentId() == null) {
      log.warn("Unable to parse assignment request payload");
      return;
    }
    if (decisionEngine == null) {
      log.warn("No decision engine configured, skipping assignment proposal");
      return;
    }
    DecisionResult result = decisionEngine.proposeAssignments(request);
    logDecisionResult(request.incidentId(), result);
    publishDecisionProposal(request.incidentId(), result);
  }

  private void ensureApiQueue() {
    if (apiQueueDeclared.compareAndSet(false, true)) {
      brokerClient.declareQueue(Queues.SDMIS_API.queue(), durableQueue);
    }
  }

  private AssignmentRequest extractRequest(EventMessage message) {
    if (message == null || message.body() == null) {
      return null;
    }
    JsonNode payloadNode = message.body().get("payload");
    if (payloadNode == null || !payloadNode.isObject()) {
      log.warn("Missing payload in event message");
      return null;
    }
    try {
      AssignmentRequestPayload payload =
          objectMapper.treeToValue(payloadNode, AssignmentRequestPayload.class);
      if (payload == null || payload.incidentId() == null) {
        return null;
      }
      List<VehicleNeed> needs = new ArrayList<>();
      if (payload.vehiclesNeeded() != null) {
        for (VehicleNeedPayload need : payload.vehiclesNeeded()) {
          if (need == null
              || need.incidentPhaseId() == null
              || need.vehicleTypeId() == null
              || need.quantity() == null
              || need.quantity() <= 0) {
            continue;
          }
          needs.add(new VehicleNeed(need.incidentPhaseId(), need.vehicleTypeId(), need.quantity()));
        }
      }
      return new AssignmentRequest(payload.incidentId(), needs);
    } catch (JsonProcessingException e) {
      log.warn("Invalid assignment request payload: {}", e.getMessage());
      return null;
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
    payload.put("vehicles_to_send", proposalPayload(result.proposals()));
    payload.put("missing", missingPayload(result.missingVehicles()));

    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("event", Events.ASSIGNMENT_PROPOSAL.key());
    envelope.put("payload", payload);

    try {
      String message = objectMapper.writeValueAsString(envelope);
      brokerClient.publish(Queues.SDMIS_API.queue(), message);
      log.info(
          "Sent assignment proposal to {} for incident {}", Queues.SDMIS_API.queue(), incidentId);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize assignment proposal for incident {}", incidentId, e);
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
      item.put("rank", proposal.rank());
      payload.add(item);
    }
    return payload;
  }

  private List<Map<String, Object>> missingPayload(List<MissingVehicle> missingVehicles) {
    if (missingVehicles == null || missingVehicles.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> payload = new ArrayList<>();
    for (MissingVehicle missing : missingVehicles) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("incident_phase_id", missing.incidentPhaseId());
      item.put("vehicle_type_id", missing.vehicleTypeId());
      item.put("missing_quantity", missing.missingQuantity());
      payload.add(item);
    }
    return payload;
  }

  private void logDecisionResult(UUID incidentId, DecisionResult result) {
    if (result == null) {
      log.info("No decision result produced for incident {}", incidentId);
      return;
    }
    log.info(
        "Assignment proposals generated for incident {}: {} vehicle(s), {} missing entries",
        incidentId,
        result.proposals() == null ? 0 : result.proposals().size(),
        result.missingVehicles() == null ? 0 : result.missingVehicles().size());
    if (result.proposals() != null && !result.proposals().isEmpty()) {
      log.debug("Assignment proposals: {}", result.proposals());
    }
    if (result.missingVehicles() != null && !result.missingVehicles().isEmpty()) {
      log.warn("Missing vehicles for incident {}: {}", incidentId, result.missingVehicles());
    }
  }

  private record AssignmentRequestPayload(
      UUID incidentId, List<VehicleNeedPayload> vehiclesNeeded) {}

  private record VehicleNeedPayload(UUID vehicleTypeId, Integer quantity, UUID incidentPhaseId) {}
}
