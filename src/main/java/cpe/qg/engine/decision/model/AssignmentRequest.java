package cpe.qg.engine.decision.model;

import java.util.List;
import java.util.UUID;

/** Input describing the vehicles required for an incident. */
public record AssignmentRequest(UUID incidentId, List<VehicleNeed> vehiclesNeeded) {}
