package cpe.qg.engine.decision.model;

import java.util.List;

/** Result of the decision engine execution for a single incident. */
public record DecisionResult(
    List<VehicleAssignmentProposal> proposals, List<MissingVehicle> missingVehicles) {}
