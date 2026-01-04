package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VehicleAssignmentRead(UUID vehicleId, UUID incidentPhaseId, String unassignedAt) {}
