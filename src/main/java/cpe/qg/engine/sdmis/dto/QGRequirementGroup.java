package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGRequirementGroup(
    UUID groupId,
    String label,
    String rule,
    Integer minTotal,
    Integer maxTotal,
    Integer priority,
    Boolean isHard,
    List<QGRequirement> requirements) {}
