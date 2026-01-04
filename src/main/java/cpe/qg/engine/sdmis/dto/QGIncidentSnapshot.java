package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGIncidentSnapshot(
    UUID incidentId,
    Double latitude,
    Double longitude,
    String address,
    String city,
    String zipcode,
    String status) {}
