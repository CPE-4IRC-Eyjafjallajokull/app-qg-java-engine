package cpe.qg.engine.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralised configuration built from the environment and an optional .env file.
 */
public final class EnvironmentConfig {

    private final RabbitConfig rabbitConfig;
    private final PostgresConfig postgresConfig;

    private EnvironmentConfig(RabbitConfig rabbitConfig, PostgresConfig postgresConfig) {
        this.rabbitConfig = rabbitConfig;
        this.postgresConfig = postgresConfig;
    }

    public static EnvironmentConfig load() {
        return from(Collections.emptyMap());
    }

    /**
     * Allows tests or callers to override specific environment values.
     */
    public static EnvironmentConfig from(Map<String, String> overrides) {
        EnvLoader env = EnvLoader.create(overrides);

        RabbitConfig rabbit = new RabbitConfig(
                env.required("RABBITMQ_URI"),
                env.optionalList("RABBITMQ_QUEUES", env.optional("RABBITMQ_QUEUE", "incidents")),
                env.optionalBoolean("RABBITMQ_QUEUE_DURABLE", true));

        PostgresConfig postgres = new PostgresConfig(
                env.required("POSTGRES_URL"),
                env.optional("POSTGRES_USER", env.optional("POSTGRES_USERNAME", null)),
                env.optional("POSTGRES_PASSWORD", env.optional("POSTGRES_PASS", null)),
                env.optionalInt("POSTGRES_POOL_SIZE", 5),
                env.optionalLong("POSTGRES_CONNECTION_TIMEOUT_MS", 30_000L)
        );

        return new EnvironmentConfig(rabbit, postgres);
    }

    public RabbitConfig rabbit() {
        return rabbitConfig;
    }

    public PostgresConfig postgres() {
        return postgresConfig;
    }

    /**
     * Lightweight helper to pull variables from .env files with sensible defaults.
     */
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
            throw new IllegalStateException("Missing required environment variable: " + String.join(" or ", keys));
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

        List<String> optionalList(String key, String fallback) {
            String raw = values.get(key);
            String source = (raw == null || raw.isBlank()) ? fallback : raw;
            if (source == null || source.isBlank()) {
                return List.of();
            }

            return Arrays.stream(source.split(","))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .toList();
        }

        int optionalInt(String key, int fallback) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Environment variable %s must be an integer".formatted(key), e);
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
                throw new IllegalStateException("Environment variable %s must be a number".formatted(key), e);
            }
        }
    }
}
