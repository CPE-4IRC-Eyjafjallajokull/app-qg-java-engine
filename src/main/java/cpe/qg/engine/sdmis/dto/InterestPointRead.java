package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InterestPointRead(
    UUID interestPointId,
    String name,
    String address,
    String city,
    String zipcode,
    Double latitude,
    Double longitude) {}
