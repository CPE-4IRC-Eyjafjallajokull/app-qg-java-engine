package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGResourcePlanningRead(
    UUID incidentId,
    List<QGPhaseRequirements> phaseRequirements,
    List<QGPhaseVehicleAvailability> availability) {}
