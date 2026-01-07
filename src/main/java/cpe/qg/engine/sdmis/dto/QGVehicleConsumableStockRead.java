package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGVehicleConsumableStockRead(
    QGConsumableTypeRef consumableType, String currentQuantity, String lastUpdate) {}
