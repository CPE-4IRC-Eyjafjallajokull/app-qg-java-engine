package cpe.qg.engine.decision.model;

import java.util.UUID;

/** Proposed vehicle assignment with a score and rationale for auditing. */
public record VehicleAssignmentProposal(
    UUID incidentPhaseId,
    UUID vehicleId,
    Double distanceKm,
    Double estimatedTimeMin,
    RouteGeometry routeGeometry,
    Double energyLevel,
    double score,
    String rationale) {}
