package cpe.qg.engine.service;

/**
 * Contract for handling incoming messages from a queue.
 */
@FunctionalInterface
public interface MessageHandler {

    void handle(IncomingMessage message) throws Exception;
}
