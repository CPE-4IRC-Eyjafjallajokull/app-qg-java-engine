package cpe.qg.engine.sdmis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import cpe.qg.engine.auth.AuthStrategy;
import cpe.qg.engine.auth.keycloak.KeycloakAuthStrategy;
import cpe.qg.engine.auth.keycloak.KeycloakTokenProvider;
import cpe.qg.engine.config.EnvironmentConfig;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Factory to build SDMIS API clients with Keycloak authentication wired in. */
public final class SdmisApiClientFactory {

  private SdmisApiClientFactory() {}

  public static SdmisApiClient create(EnvironmentConfig environmentConfig) {
    Objects.requireNonNull(environmentConfig, "Environment config is required");

    long connectTimeoutMs =
        Math.min(
            environmentConfig.keycloak().timeoutMs(), environmentConfig.sdmisApi().timeoutMs());
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    KeycloakTokenProvider tokenProvider =
        new KeycloakTokenProvider(
            environmentConfig.keycloak(), httpClient, mapper, Clock.systemUTC());
    AuthStrategy authStrategy = new KeycloakAuthStrategy(tokenProvider);

    return new SdmisApiClient(environmentConfig.sdmisApi(), authStrategy, httpClient, mapper);
  }
}
