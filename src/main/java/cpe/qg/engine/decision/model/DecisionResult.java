package cpe.qg.engine.decision.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Result of the decision engine execution for a single incident. */
public record DecisionResult(
    List<VehicleAssignmentProposal> proposals, Map<UUID, Integer> missingByVehicleType) {}
