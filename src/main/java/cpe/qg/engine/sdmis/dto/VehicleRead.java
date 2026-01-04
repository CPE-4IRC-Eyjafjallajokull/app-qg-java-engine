package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VehicleRead(
    UUID vehicleId,
    UUID vehicleTypeId,
    String immatriculation,
    UUID energyId,
    Double energyLevel,
    UUID baseInterestPointId,
    UUID statusId) {}
