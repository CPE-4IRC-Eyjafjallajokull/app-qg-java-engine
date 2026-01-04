package cpe.qg.engine.config;

import java.util.Objects;

/** Immutable RabbitMQ settings loaded from the environment. */
public record RabbitConfig(String uri, boolean durableQueue) {

  public RabbitConfig {
    Objects.requireNonNull(uri, "RabbitMQ URI is required");
  }
}
