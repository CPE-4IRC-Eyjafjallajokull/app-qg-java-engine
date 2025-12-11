package com.qg.engine;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CommunicationService {

    private final String rabbitDsn;
    private final String postgresDsn;

    private Connection rabbitConn; // com.rabbitmq.client.Connection
    private Channel rabbitChannel;
    private java.sql.Connection pgConn; // java.sql.Connection

    private static final String QUEUE_NAME = "incidents";

    public CommunicationService() {
        this.rabbitDsn = System.getenv("RABBITMQ_DSN");
        this.postgresDsn = System.getenv("POSTGRES_DSN");
    }

    public void start() throws Exception {
        connectPostgres();
        connectRabbit();
        consumeMessages();
    }

    private void connectPostgres() throws SQLException {
        pgConn = DriverManager.getConnection(postgresDsn);
        System.out.println("[PG] Connected to PostgreSQL");
    }

    private void connectRabbit() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(rabbitDsn);

        rabbitConn = factory.newConnection(); // com.rabbitmq.client.Connection
        rabbitChannel = rabbitConn.createChannel();

        rabbitChannel.queueDeclare(QUEUE_NAME, true, false, false, null);

        System.out.println("[RABBIT] Connected to RabbitMQ");
    }

    private void consumeMessages() throws Exception {
        DeliverCallback callback = (tag, delivery) -> {
            String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println("[RABBIT] Received message: " + msg);

            saveToDatabase(msg);
        };

        rabbitChannel.basicConsume(QUEUE_NAME, true, callback, tag -> {});
        System.out.println("[RABBIT] Listening for messages...");
    }

    private void saveToDatabase(String payload) {
        try {
            String sql = "INSERT INTO event_log(message) VALUES (?)";
            PreparedStatement stmt = pgConn.prepareStatement(sql);
            stmt.setString(1, payload);
            stmt.executeUpdate();

            System.out.println("[PG] Stored message in PostgreSQL");
        } catch (SQLException e) {
            System.err.println("[PG] Error storing message: " + e.getMessage());
        }
    }
}
