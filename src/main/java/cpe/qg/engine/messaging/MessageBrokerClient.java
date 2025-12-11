package cpe.qg.engine.messaging;

import com.rabbitmq.client.DeliverCallback;

public interface MessageBrokerClient extends AutoCloseable {

    void connect();

    void declareQueue(String queueName, boolean durable);

    void publish(String queueName, String message);

    void consume(String queueName, DeliverCallback deliverCallback);

    void healthCheck();

    @Override
    void close();
}
