package cpe.qg.engine.auth.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import cpe.qg.engine.config.KeycloakConfig;
import cpe.qg.engine.logging.LoggerProvider;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/** Retrieves and caches Keycloak access tokens using client credentials. */
public final class KeycloakTokenProvider {

  private final KeycloakConfig config;
  private final HttpClient httpClient;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final Logger log = LoggerProvider.getLogger(KeycloakTokenProvider.class);
  private final Object lock = new Object();
  private volatile Token cachedToken;

  public KeycloakTokenProvider(
      KeycloakConfig config, HttpClient httpClient, ObjectMapper mapper, Clock clock) {
    this.config = Objects.requireNonNull(config, "Keycloak config is required");
    this.httpClient = Objects.requireNonNull(httpClient, "HttpClient is required");
    this.mapper = Objects.requireNonNull(mapper, "ObjectMapper is required");
    this.clock = Objects.requireNonNull(clock, "Clock is required");
  }

  public String getAccessToken() {
    Token token = cachedToken;
    if (token != null && token.isValid(clock, config.tokenExpirySkewSeconds())) {
      return token.value();
    }
    synchronized (lock) {
      token = cachedToken;
      if (token != null && token.isValid(clock, config.tokenExpirySkewSeconds())) {
        return token.value();
      }
      Token refreshed = fetchToken();
      cachedToken = refreshed;
      return refreshed.value();
    }
  }

  private Token fetchToken() {
    String form =
        buildForm(
            Map.of(
                "grant_type", "client_credentials",
                "client_id", config.clientId(),
                "client_secret", config.clientSecret()));

    HttpRequest request =
        HttpRequest.newBuilder(config.tokenEndpoint())
            .timeout(Duration.ofMillis(config.timeoutMs()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Keycloak token request failed (status=%s)".formatted(response.statusCode()));
      }

      KeycloakTokenResponse tokenResponse =
          mapper.readValue(response.body(), KeycloakTokenResponse.class);
      if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
        throw new IllegalStateException("Keycloak response did not include an access token");
      }

      long expiresIn = tokenResponse.expiresIn() > 0 ? tokenResponse.expiresIn() : 60;
      long expiresAtEpochSeconds = clock.instant().getEpochSecond() + expiresIn;
      log.debug("Fetched Keycloak token (expiresIn={}s)", expiresIn);
      return new Token(tokenResponse.accessToken(), expiresAtEpochSeconds);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse Keycloak token response", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Keycloak token request interrupted", e);
    }
  }

  private String buildForm(Map<String, String> values) {
    Map<String, String> ordered = new LinkedHashMap<>(values);
    StringBuilder form = new StringBuilder();
    for (Map.Entry<String, String> entry : ordered.entrySet()) {
      if (form.length() > 0) {
        form.append('&');
      }
      form.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
      form.append('=');
      form.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return form.toString();
  }

  private record Token(String value, long expiresAtEpochSeconds) {
    boolean isValid(Clock clock, long skewSeconds) {
      long now = clock.instant().getEpochSecond();
      return now + skewSeconds < expiresAtEpochSeconds;
    }
  }
}
