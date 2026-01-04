package cpe.qg.engine.auth.keycloak;

import cpe.qg.engine.auth.AuthStrategy;
import java.net.http.HttpRequest;
import java.util.Objects;

/** Keycloak-based authentication strategy for outbound HTTP requests. */
public final class KeycloakAuthStrategy implements AuthStrategy {

  private final KeycloakTokenProvider tokenProvider;

  public KeycloakAuthStrategy(KeycloakTokenProvider tokenProvider) {
    this.tokenProvider = Objects.requireNonNull(tokenProvider, "Token provider is required");
  }

  @Override
  public void apply(HttpRequest.Builder builder) {
    String token = tokenProvider.getAccessToken();
    builder.header("Authorization", "Bearer " + token);
  }
}
