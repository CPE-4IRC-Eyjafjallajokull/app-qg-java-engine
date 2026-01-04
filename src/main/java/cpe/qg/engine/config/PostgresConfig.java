package cpe.qg.engine.config;

import java.util.Objects;

/** Immutable PostgreSQL settings loaded from the environment. */
public record PostgresConfig(
    String jdbcUrl, String username, String password, int maxPoolSize, long connectionTimeoutMs) {

  public PostgresConfig {
    Objects.requireNonNull(jdbcUrl, "PostgreSQL JDBC URL is required");
    if (maxPoolSize <= 0) {
      throw new IllegalArgumentException("maxPoolSize must be positive");
    }
    if (connectionTimeoutMs <= 0) {
      throw new IllegalArgumentException("connectionTimeoutMs must be positive");
    }
  }
}
