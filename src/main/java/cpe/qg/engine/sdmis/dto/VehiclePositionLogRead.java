package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VehiclePositionLogRead(
    UUID vehicleId, Double latitude, Double longitude, String timestamp) {}
