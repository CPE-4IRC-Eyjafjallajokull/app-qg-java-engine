package cpe.qg.engine.decision.model;

import java.util.UUID;

/** Proposed vehicle assignment with a score. */
public record VehicleAssignmentProposal(
    UUID incidentPhaseId,
    UUID vehicleId,
    Double distanceKm,
    Double estimatedTimeMin,
    RouteGeometry routeGeometry,
    Double energyLevel,
    double score,
    int rank) {}
