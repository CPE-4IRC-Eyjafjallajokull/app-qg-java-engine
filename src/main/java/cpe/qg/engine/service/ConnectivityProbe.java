package cpe.qg.engine.service;

import cpe.qg.engine.database.DatabaseClient;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.messaging.MessageBrokerClient;
import org.slf4j.Logger;

/** Runs lightweight checks to validate connectivity to external services. */
public final class ConnectivityProbe {

  private final DatabaseClient databaseClient;
  private final MessageBrokerClient messageBrokerClient;
  private final Logger log = LoggerProvider.getLogger(ConnectivityProbe.class);

  public ConnectivityProbe(DatabaseClient databaseClient, MessageBrokerClient messageBrokerClient) {
    this.databaseClient = databaseClient;
    this.messageBrokerClient = messageBrokerClient;
  }

  public void run() {
    databaseClient.connect();
    databaseClient.healthCheck();

    messageBrokerClient.connect();
    messageBrokerClient.healthCheck();

    log.info("Connectivity checks completed");
  }
}
