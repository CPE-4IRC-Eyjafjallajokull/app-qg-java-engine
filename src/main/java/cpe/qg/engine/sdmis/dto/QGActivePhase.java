package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGActivePhase(
    UUID incidentPhaseId, UUID incidentId, UUID phaseTypeId, Integer priority) {}
