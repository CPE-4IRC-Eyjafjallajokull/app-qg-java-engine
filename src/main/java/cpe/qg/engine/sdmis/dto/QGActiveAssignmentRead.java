package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGActiveAssignmentRead(
    UUID vehicleAssignmentId, UUID incidentPhaseId, String assignedAt, UUID assignedByOperatorId) {}
