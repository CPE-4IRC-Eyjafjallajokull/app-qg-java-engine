package cpe.qg.engine.config;

import cpe.qg.engine.decision.model.DecisionCriteria;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Centralised configuration built from the environment and an optional .env file. */
public final class EnvironmentConfig {

  private final RabbitConfig rabbitConfig;
  private final PostgresConfig postgresConfig;
  private final KeycloakConfig keycloakConfig;
  private final SdmisApiConfig sdmisApiConfig;
  private final DecisionCriteria decisionCriteria;

  private EnvironmentConfig(
      RabbitConfig rabbitConfig,
      PostgresConfig postgresConfig,
      KeycloakConfig keycloakConfig,
      SdmisApiConfig sdmisApiConfig,
      DecisionCriteria decisionCriteria) {
    this.rabbitConfig = rabbitConfig;
    this.postgresConfig = postgresConfig;
    this.keycloakConfig = keycloakConfig;
    this.sdmisApiConfig = sdmisApiConfig;
    this.decisionCriteria = decisionCriteria;
  }

  public static EnvironmentConfig load() {
    return from(Collections.emptyMap());
  }

  /** Allows tests or callers to override specific environment values. */
  public static EnvironmentConfig from(Map<String, String> overrides) {
    EnvLoader env = EnvLoader.create(overrides);

    RabbitConfig rabbit =
        new RabbitConfig(
            env.required("RABBITMQ_URI"), env.optionalBoolean("RABBITMQ_QUEUE_DURABLE", true));

    PostgresConfig postgres =
        new PostgresConfig(
            env.required("POSTGRES_URL"),
            env.optional("POSTGRES_USER", env.optional("POSTGRES_USERNAME", null)),
            env.optional("POSTGRES_PASSWORD", env.optional("POSTGRES_PASS", null)),
            env.optionalInt("POSTGRES_POOL_SIZE", 5),
            env.optionalLong("POSTGRES_CONNECTION_TIMEOUT_MS", 30_000L));

    KeycloakConfig keycloak =
        new KeycloakConfig(
            env.optional("KEYCLOAK_ISSUER", "http://localhost:8080/realms/sdmis"),
            env.required("KEYCLOAK_CLIENT_ID"),
            env.required("KEYCLOAK_CLIENT_SECRET"),
            env.optionalLong("KEYCLOAK_TIMEOUT_MS", 3_000L),
            env.optionalLong("KEYCLOAK_TOKEN_EXPIRY_SKEW_SECONDS", 30L));

    SdmisApiConfig sdmisApi =
        new SdmisApiConfig(
            env.optional("SDMIS_API_BASE_URL", "http://localhost:3001"),
            env.optionalLong("SDMIS_API_TIMEOUT_MS", 5_000L));

    DecisionCriteria criteria =
        new DecisionCriteria(
            env.optionalDouble("DECISION_MAX_DISTANCE_KM", null),
            env.optionalDouble("DECISION_MIN_ENERGY_LEVEL", null));

    return new EnvironmentConfig(rabbit, postgres, keycloak, sdmisApi, criteria);
  }

  public RabbitConfig rabbit() {
    return rabbitConfig;
  }

  public PostgresConfig postgres() {
    return postgresConfig;
  }

  public KeycloakConfig keycloak() {
    return keycloakConfig;
  }

  public SdmisApiConfig sdmisApi() {
    return sdmisApiConfig;
  }

  public DecisionCriteria decisionCriteria() {
    return decisionCriteria;
  }

  /** Lightweight helper to pull variables from .env files with sensible defaults. */
  static final class EnvLoader {
    private final Map<String, String> values;

    private EnvLoader(Map<String, String> values) {
      this.values = values;
    }

    static EnvLoader create(Map<String, String> overrides) {
      Map<String, String> merged = new HashMap<>();
      Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
      dotenv.entries().forEach(entry -> merged.put(entry.getKey(), entry.getValue()));
      merged.putAll(System.getenv());
      merged.putAll(overrides);
      return new EnvLoader(Collections.unmodifiableMap(merged));
    }

    String required(String... keys) {
      for (String key : keys) {
        String value = values.get(key);
        if (value != null && !value.isBlank()) {
          return value.trim();
        }
      }
      throw new IllegalStateException(
          "Missing required environment variable: " + String.join(" or ", keys));
    }

    String optional(String key, String fallback) {
      String value = values.get(key);
      return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    boolean optionalBoolean(String key, boolean fallback) {
      String value = values.get(key);
      if (value == null || value.isBlank()) {
        return fallback;
      }
      return Boolean.parseBoolean(value.trim());
    }

    int optionalInt(String key, int fallback) {
      String value = values.get(key);
      if (value == null || value.isBlank()) {
        return fallback;
      }
      try {
        return Integer.parseInt(value.trim());
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "Environment variable %s must be an integer".formatted(key), e);
      }
    }

    long optionalLong(String key, long fallback) {
      String value = values.get(key);
      if (value == null || value.isBlank()) {
        return fallback;
      }
      try {
        return Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "Environment variable %s must be a number".formatted(key), e);
      }
    }

    Double optionalDouble(String key, Double fallback) {
      String value = values.get(key);
      if (value == null || value.isBlank()) {
        return fallback;
      }
      try {
        return Double.parseDouble(value.trim());
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "Environment variable %s must be a number".formatted(key), e);
      }
    }
  }
}
