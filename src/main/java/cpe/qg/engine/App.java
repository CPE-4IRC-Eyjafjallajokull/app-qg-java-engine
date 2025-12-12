package cpe.qg.engine;

import cpe.qg.engine.config.EnvironmentConfig;
import cpe.qg.engine.config.RabbitConfig;
import cpe.qg.engine.database.PostgresClient;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.RabbitMqClient;
import cpe.qg.engine.service.ConnectivityProbe;
import cpe.qg.engine.service.MessageHandler;
import cpe.qg.engine.service.QueueConsumerService;
import cpe.qg.engine.service.QueueSubscription;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Application entrypoint. Verifies connectivity and subscribes to configured queues.
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
        QueueConsumerService consumerService = new QueueConsumerService(
                rabbitMqClient,
                buildSubscriptions(rabbitConfig),
                rabbitConfig.durableQueue());

        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Closing resources...");
            closeQuietly(consumerService, "RabbitMQ consumer service");
            closeQuietly(postgresClient, "PostgreSQL client");
            latch.countDown();
        }, "qg-shutdown"));

        try {
            ConnectivityProbe probe = new ConnectivityProbe(postgresClient, rabbitMqClient);
            probe.run();

            consumerService.start();
            log.info("Engine is running. Subscribed to queues: {}. Press Ctrl+C to exit.", String.join(", ", rabbitConfig.queues()));

            latch.await();
        } catch (Exception e) {
            log.error("Startup failed", e);
            System.exit(1);
        }
    }

    private static List<QueueSubscription> buildSubscriptions(RabbitConfig rabbitConfig) {
        MessageHandler handler = message -> log.info("Received message on {}: {}", message.queue(), message.payload());
        return rabbitConfig.queues().stream()
                .map(queue -> new QueueSubscription(queue, handler))
                .toList();
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
