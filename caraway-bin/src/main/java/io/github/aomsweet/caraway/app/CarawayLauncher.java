package io.github.aomsweet.caraway.app;

import ch.qos.logback.classic.LoggerContext;
import io.github.aomsweet.caraway.CarawayBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * @author aomsweet
 */
public class CarawayLauncher {

    private static final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    private static final Logger logger = LoggerFactory.getLogger(CarawayLauncher.class);

    public static void main(String[] args) {
        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        String name = mx.getName();
        logger.info("Starting {} on {} with process id {} ({})", CarawayLauncher.class.getSimpleName(),
            name.substring(name.indexOf('@') + 1), mx.getPid(), System.getProperty("user.dir"));
        CarawayBootstrap bootstrap = new CarawayBootstrap()
            .withPort(2228);
        bootstrap.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            bootstrap.close();
            loggerContext.stop();
        }));
    }

}
