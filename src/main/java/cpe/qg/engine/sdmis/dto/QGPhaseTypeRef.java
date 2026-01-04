package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGPhaseTypeRef(UUID phaseTypeId, String code, String label) {}
