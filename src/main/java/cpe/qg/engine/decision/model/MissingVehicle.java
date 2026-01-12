package cpe.qg.engine.decision.model;

import java.util.UUID;

/** Missing vehicles for a given phase and type. */
public record MissingVehicle(UUID incidentPhaseId, UUID vehicleTypeId, int missingQuantity) {}
