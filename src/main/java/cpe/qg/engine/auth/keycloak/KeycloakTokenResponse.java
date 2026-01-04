package cpe.qg.engine.auth.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Keycloak token response payload for client credentials flow. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("token_type") String tokenType) {}
