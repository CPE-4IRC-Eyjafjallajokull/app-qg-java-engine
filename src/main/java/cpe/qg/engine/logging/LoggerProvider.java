package cpe.qg.engine.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Central place to obtain pre-configured loggers and route JUL logs to SLF4J.
 */
public final class LoggerProvider {
    private LoggerProvider() {
    }

    public static Logger getLogger(Class<?> target) {
        return LoggerFactory.getLogger(target);
    }

    public static void installBridge() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }
}
