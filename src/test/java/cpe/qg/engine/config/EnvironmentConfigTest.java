package cpe.qg.engine.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentConfigTest {

    @Test
    void loadsRabbitAndPostgresFromOverrides() {
        Map<String, String> overrides = Map.of(
                "RABBITMQ_URI", "amqp://guest:guest@localhost:5672",
                "POSTGRES_URL", "jdbc:postgresql://localhost:5432/qg",
                "POSTGRES_USER", "app",
                "POSTGRES_PASSWORD", "secret",
                "POSTGRES_POOL_SIZE", "10",
                "RABBITMQ_QUEUE", "demo"
        );

        EnvironmentConfig config = EnvironmentConfig.from(overrides);

        assertThat(config.rabbit().uri()).isEqualTo("amqp://guest:guest@localhost:5672");
        assertThat(config.rabbit().queueName()).isEqualTo("demo");
        assertThat(config.postgres().jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/qg");
        assertThat(config.postgres().username()).isEqualTo("app");
        assertThat(config.postgres().password()).isEqualTo("secret");
        assertThat(config.postgres().maxPoolSize()).isEqualTo(10);
    }

    @Test
    void throwsWhenRequiredEnvMissing() {
        Map<String, String> overrides = Map.of(
                "RABBITMQ_URI", "amqp://localhost",
                "POSTGRES_URL", "   "
        );

        assertThatThrownBy(() -> EnvironmentConfig.from(overrides))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_URL");
    }

    @Test
    void failsOnInvalidNumericValues() {
        Map<String, String> overrides = Map.of(
                "RABBITMQ_URI", "amqp://localhost",
                "POSTGRES_URL", "jdbc:postgresql://localhost:5432/qg",
                "POSTGRES_POOL_SIZE", "not-a-number"
        );

        assertThatThrownBy(() -> EnvironmentConfig.from(overrides))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_POOL_SIZE");
    }
}
