package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGRouteRequest(
    QGRoutePoint from, QGRoutePoint to, @JsonProperty("snap_start") Boolean snapStart) {}
