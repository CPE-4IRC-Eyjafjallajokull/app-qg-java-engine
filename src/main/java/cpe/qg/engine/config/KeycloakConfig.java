package cpe.qg.engine.config;

import java.net.URI;
import java.util.Objects;

/** Immutable Keycloak settings loaded from the environment. */
public record KeycloakConfig(
    String issuerUrl,
    String clientId,
    String clientSecret,
    long timeoutMs,
    long tokenExpirySkewSeconds) {

  public KeycloakConfig {
    Objects.requireNonNull(issuerUrl, "Keycloak issuer URL is required");
    Objects.requireNonNull(clientId, "Keycloak clientId is required");
    Objects.requireNonNull(clientSecret, "Keycloak clientSecret is required");
    if (timeoutMs <= 0) {
      throw new IllegalArgumentException("timeoutMs must be positive");
    }
    if (tokenExpirySkewSeconds < 0) {
      throw new IllegalArgumentException("tokenExpirySkewSeconds must be zero or positive");
    }
  }

  public URI tokenEndpoint() {
    String trimmed =
        issuerUrl.endsWith("/") ? issuerUrl.substring(0, issuerUrl.length() - 1) : issuerUrl;
    return URI.create(trimmed + "/protocol/openid-connect/token");
  }
}
