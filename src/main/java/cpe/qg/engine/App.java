package cpe.qg.engine;

import cpe.qg.engine.config.EnvironmentConfig;
import cpe.qg.engine.config.RabbitConfig;
import cpe.qg.engine.database.PostgresClient;
import cpe.qg.engine.decision.api.DecisionEngine;
import cpe.qg.engine.decision.impl.DistanceEnergyScoringStrategy;
import cpe.qg.engine.decision.impl.SdmisDecisionDataSource;
import cpe.qg.engine.decision.impl.VehicleAssignmentDecisionEngine;
import cpe.qg.engine.events.EventDispatcher;
import cpe.qg.engine.events.EventPayloadParser;
import cpe.qg.engine.handlers.EventHandler;
import cpe.qg.engine.handlers.IncidentHandler;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.QueueListener;
import cpe.qg.engine.messaging.Queues;
import cpe.qg.engine.messaging.RabbitMqClient;
import cpe.qg.engine.sdmis.SdmisApiClient;
import cpe.qg.engine.sdmis.SdmisApiClientFactory;
import cpe.qg.engine.service.ConnectivityProbe;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;

/** Application entrypoint. Verifies connectivity and starts the single RabbitMQ listener. */
public class App {

  private static final Logger log = LoggerProvider.getLogger(App.class);

  public static void main(String[] args) {
    LoggerProvider.installBridge();
    log.info("Starting QG Java Engine...");

    EnvironmentConfig env = EnvironmentConfig.load();

    PostgresClient postgresClient = new PostgresClient(env.postgres());
    RabbitConfig rabbitConfig = env.rabbit();
    RabbitMqClient rabbitMqClient = new RabbitMqClient(rabbitConfig);
    // print rabbitmq config for debugging
    log.info(
        "RabbitMQ Config: uri={}, durableQueue={}",
        rabbitConfig.uri(),
        rabbitConfig.durableQueue());
    List<EventHandler> handlers = buildHandlers(env, rabbitConfig, rabbitMqClient);
    EventDispatcher dispatcher = new EventDispatcher(handlers);
    QueueListener queueListener =
        new QueueListener(
            rabbitMqClient,
            Queues.subscriptions(),
            rabbitConfig.durableQueue(),
            dispatcher,
            new EventPayloadParser());

    CountDownLatch latch = new CountDownLatch(1);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Shutdown signal received. Closing resources...");
                  closeQuietly(queueListener, "RabbitMQ listener");
                  closeQuietly(postgresClient, "PostgreSQL client");
                  latch.countDown();
                },
                "qg-shutdown"));

    try {
      ConnectivityProbe probe = new ConnectivityProbe(postgresClient, rabbitMqClient);
      probe.run();

      queueListener.start();
      log.info(
          "Engine is running. Listening on queues {} for events {}. Press Ctrl+C to exit.",
          Queues.subscriptions(),
          handlers.stream().map(EventHandler::eventKey).toList());

      latch.await();
    } catch (Exception e) {
      log.error("Startup failed", e);
      System.exit(1);
    }
  }

  private static List<EventHandler> buildHandlers(
      EnvironmentConfig env, RabbitConfig rabbitConfig, RabbitMqClient brokerClient) {
    SdmisApiClient sdmisApiClient = SdmisApiClientFactory.create(env);
    DecisionEngine decisionEngine =
        new VehicleAssignmentDecisionEngine(
            new SdmisDecisionDataSource(sdmisApiClient),
            new DistanceEnergyScoringStrategy(),
            env.decisionCriteria());
    IncidentHandler incidentHandler =
        new IncidentHandler(brokerClient, rabbitConfig.durableQueue(), decisionEngine);
    return List.of(incidentHandler);
  }

  private static void closeQuietly(AutoCloseable resource, String name) {
    if (resource == null) {
      return;
    }
    try {
      resource.close();
    } catch (Exception e) {
      log.warn("Error while closing {}", name, e);
    }
  }
}
