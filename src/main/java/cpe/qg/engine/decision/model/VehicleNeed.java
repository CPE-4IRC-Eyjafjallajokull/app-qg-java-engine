package cpe.qg.engine.decision.model;

import java.util.UUID;

/** Requested quantity of a vehicle type for a specific incident phase. */
public record VehicleNeed(UUID incidentPhaseId, UUID vehicleTypeId, int quantity) {}
