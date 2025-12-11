package cpe.qg.engine;

import cpe.qg.engine.config.EnvironmentConfig;
import cpe.qg.engine.database.PostgresClient;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.RabbitMqClient;
import cpe.qg.engine.service.ConnectivityProbe;
import cpe.qg.engine.service.RabbitTestHarness;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;

/**
 * Minimal entrypoint that verifies external connectivity without starting the full pipeline.
 */
public class App {

    private static final String TEST_CONSUME_QUEUE = "test_event";
    private static final String TEST_PUBLISH_QUEUE = "test_pub";
    private static final Logger log = LoggerProvider.getLogger(App.class);

    public static void main(String[] args) {
        LoggerProvider.installBridge();
        log.info("Starting QG Java Engine...");

        EnvironmentConfig env = EnvironmentConfig.load();

        PostgresClient postgresClient = new PostgresClient(env.postgres());
        RabbitMqClient rabbitMqClient = new RabbitMqClient(env.rabbit());
        RabbitTestHarness testHarness = new RabbitTestHarness(rabbitMqClient, TEST_CONSUME_QUEUE, TEST_PUBLISH_QUEUE);
        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Closing resources...");
            closeQuietly(testHarness, "RabbitMQ harness");
            closeQuietly(postgresClient, "PostgreSQL client");
            latch.countDown();
        }, "qg-shutdown"));

        try {
            ConnectivityProbe probe = new ConnectivityProbe(postgresClient, rabbitMqClient);
            probe.run();

            testHarness.start();
            log.info("Engine is running. Consuming '{}' and published sample to '{}'. Press Ctrl+C to exit.", TEST_CONSUME_QUEUE, TEST_PUBLISH_QUEUE);

            latch.await();
        } catch (Exception e) {
            log.error("Startup failed", e);
            System.exit(1);
        }
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
