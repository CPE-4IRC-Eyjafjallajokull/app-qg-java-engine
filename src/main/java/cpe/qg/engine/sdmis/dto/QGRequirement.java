package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGRequirement(
    QGVehicleTypeRef vehicleType,
    Integer minQuantity,
    Integer maxQuantity,
    Boolean mandatory,
    Integer preferenceRank) {}
