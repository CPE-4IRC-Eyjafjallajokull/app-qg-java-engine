package cpe.qg.engine.decision.model;

import java.util.UUID;

/** Proposed vehicle assignment with a score and rationale for auditing. */
public record VehicleAssignmentProposal(
    UUID incidentId,
    UUID incidentPhaseId,
    UUID phaseTypeId,
    UUID vehicleId,
    UUID vehicleTypeId,
    Double distanceKm,
    Double energyLevel,
    double score,
    String rationale) {}
