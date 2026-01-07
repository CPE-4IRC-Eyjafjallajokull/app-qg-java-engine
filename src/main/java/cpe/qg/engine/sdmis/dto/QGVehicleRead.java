package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGVehicleRead(
    UUID vehicleId,
    String immatriculation,
    QGVehicleTypeRef vehicleType,
    QGEnergyRef energy,
    Double energyLevel,
    QGVehicleStatusRef status,
    QGBaseInterestPointRead baseInterestPoint,
    QGVehiclePositionRead currentPosition,
    List<QGVehicleConsumableStockRead> consumableStocks,
    QGActiveAssignmentRead activeAssignment) {}
