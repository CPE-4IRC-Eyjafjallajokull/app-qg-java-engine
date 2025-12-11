package cpe.qg.engine.database;

import cpe.qg.engine.config.PostgresConfig;
import cpe.qg.engine.logging.LoggerProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgreSQL connector backed by HikariCP.
 */
public class PostgresClient implements DatabaseClient {

    private final PostgresConfig config;
    private final Logger log = LoggerProvider.getLogger(PostgresClient.class);
    private HikariDataSource dataSource;

    public PostgresClient(PostgresConfig config) {
        this.config = config;
    }

    @Override
    public void connect() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        if (config.username() != null && !config.username().isBlank()) {
            hikari.setUsername(config.username());
        }
        if (config.password() != null) {
            hikari.setPassword(config.password());
        }
        hikari.setMaximumPoolSize(config.maxPoolSize());
        hikari.setConnectionTimeout(config.connectionTimeoutMs());
        hikari.setPoolName("qg-engine-pg");

        dataSource = new HikariDataSource(hikari);
        log.info("PostgreSQL pool initialised (url={}, maxPoolSize={})", config.jdbcUrl(), config.maxPoolSize());
    }

    @Override
    public void healthCheck() {
        ensureConnected();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
            stmt.execute();
            log.info("PostgreSQL connectivity check succeeded");
        } catch (SQLException e) {
            throw new IllegalStateException("PostgreSQL connectivity check failed", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        ensureConnected();
        return dataSource.getConnection();
    }

    private void ensureConnected() {
        if (dataSource == null || dataSource.isClosed()) {
            connect();
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("PostgreSQL pool closed");
        }
    }
}
