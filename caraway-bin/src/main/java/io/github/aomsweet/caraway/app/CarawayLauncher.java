package io.github.aomsweet.caraway.app;

import ch.qos.logback.classic.LoggerContext;
import io.github.aomsweet.caraway.CarawayBootstrap;
import org.slf4j.LoggerFactory;

/**
 * @author aomsweet
 */
public class CarawayLauncher {

    static LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    public static void main(String[] args) {
        CarawayBootstrap bootstrap = new CarawayBootstrap()
            .withPort(2228);
        bootstrap.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            bootstrap.close();
            loggerContext.stop();
        }));
    }

}
