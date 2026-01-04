package cpe.qg.engine.messaging;

import java.util.List;
import java.util.stream.Stream;

/** Central registry of RabbitMQ queues used by the engine. */
public enum Queues {
  SDMIS_ENGINE("sdmis_engine", Direction.SUB),
  SDMIS_API("sdmis_api", Direction.PUB);

  private final String name;
  private final Direction direction;

  Queues(String name, Direction direction) {
    this.name = name;
    this.direction = direction;
  }

  public String queue() {
    return name;
  }

  public boolean isSubscription() {
    return direction == Direction.SUB;
  }

  public boolean isPublication() {
    return direction == Direction.PUB;
  }

  public static List<String> subscriptions() {
    return stream().filter(Queues::isSubscription).map(Queues::queue).toList();
  }

  public static List<String> publications() {
    return stream().filter(Queues::isPublication).map(Queues::queue).toList();
  }

  private static Stream<Queues> stream() {
    return Stream.of(values());
  }

  private enum Direction {
    SUB,
    PUB
  }
}
