package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGPhaseVehicleAvailability(
    QGVehicleTypeRef vehicleType, Integer available, Integer assigned, Integer total) {}
