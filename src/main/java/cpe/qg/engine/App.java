package cpe.qg.engine;

import cpe.qg.engine.config.EnvironmentConfig;
import cpe.qg.engine.config.RabbitConfig;
import cpe.qg.engine.database.PostgresClient;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.events.EventDispatcher;
import cpe.qg.engine.events.EventPayloadParser;
import cpe.qg.engine.handlers.EventHandler;
import cpe.qg.engine.handlers.IncidentHandler;
import cpe.qg.engine.messaging.QueueListener;
import cpe.qg.engine.messaging.Queues;
import cpe.qg.engine.messaging.RabbitMqClient;
import cpe.qg.engine.service.ConnectivityProbe;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Application entrypoint. Verifies connectivity and starts the single RabbitMQ listener.
 */
public class App {

    private static final Logger log = LoggerProvider.getLogger(App.class);

    public static void main(String[] args) {
        LoggerProvider.installBridge();
        log.info("Starting QG Java Engine...");

        EnvironmentConfig env = EnvironmentConfig.load();

        PostgresClient postgresClient = new PostgresClient(env.postgres());
        RabbitConfig rabbitConfig = env.rabbit();
        RabbitMqClient rabbitMqClient = new RabbitMqClient(rabbitConfig);
        List<EventHandler> handlers = buildHandlers(rabbitConfig, rabbitMqClient);
        EventDispatcher dispatcher = new EventDispatcher(handlers);
        QueueListener queueListener = new QueueListener(
                rabbitMqClient,
                Queues.subscriptions(),
                rabbitConfig.durableQueue(),
                dispatcher,
                new EventPayloadParser());

        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Closing resources...");
            closeQuietly(queueListener, "RabbitMQ listener");
            closeQuietly(postgresClient, "PostgreSQL client");
            latch.countDown();
        }, "qg-shutdown"));

        try {
            ConnectivityProbe probe = new ConnectivityProbe(postgresClient, rabbitMqClient);
            probe.run();

            queueListener.start();
            log.info("Engine is running. Listening on queues {} for events {}. Press Ctrl+C to exit.",
                    Queues.subscriptions(),
                    handlers.stream().map(EventHandler::eventKey).toList());

            latch.await();
        } catch (Exception e) {
            log.error("Startup failed", e);
            System.exit(1);
        }
    }

    private static List<EventHandler> buildHandlers(RabbitConfig rabbitConfig, RabbitMqClient brokerClient) {
        IncidentHandler incidentHandler = new IncidentHandler(brokerClient, rabbitConfig.durableQueue());
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
