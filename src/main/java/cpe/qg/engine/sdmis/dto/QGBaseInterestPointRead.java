package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGBaseInterestPointRead(
    UUID interestPointId,
    String name,
    String address,
    String zipcode,
    String city,
    Double latitude,
    Double longitude) {}
