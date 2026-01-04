package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGPhaseRequirements(QGPhaseTypeRef phaseType, List<QGRequirementGroup> groups) {}
