package io.github.aomsweet.caraway.app;

import ch.qos.logback.classic.LoggerContext;
import io.github.aomsweet.caraway.CarawayServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * @author aomsweet
 */
public class CarawayApplication {

    private static final LoggerContext loggerCtx = (LoggerContext) LoggerFactory.getILoggerFactory();
    private static final Logger logger = LoggerFactory.getLogger(CarawayApplication.class);

    private static CarawayServer caraway;

    public static void main(String[] args) {
        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        String name = mx.getName();
        logger.info("Starting {} on {} with process id {} ({})", CarawayApplication.class.getSimpleName(),
            name.substring(name.indexOf('@') + 1), mx.getPid(), System.getProperty("user.dir"));
        caraway = new CarawayServer()
            // .withProxyAuthenticator((username, password) -> "admin".equals(username) && "admin".equals(password))
            .withPort(2228);
        caraway.start().whenComplete((channel, cause) -> {
            if (channel == null) {
                caraway.asyncStop(0).whenComplete((v, e) -> loggerCtx.stop());
            } else {
                Thread shutdownHookThread = new Thread(CarawayApplication::close);
                shutdownHookThread.setName("Caraway shutdown hook");
                Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            }
        });
    }

    public static void close() {
        caraway.close();
        loggerCtx.stop();
    }

}
