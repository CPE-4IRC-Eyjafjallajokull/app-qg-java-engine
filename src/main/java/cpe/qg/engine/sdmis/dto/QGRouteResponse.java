package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGRouteResponse(
    @JsonProperty("distance_m") Double distanceM,
    @JsonProperty("duration_s") Double durationS,
    QGRouteGeometry geometry) {}
