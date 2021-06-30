package io.github.aomsweet.caraway.app;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.app.logback.LogbackContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * @author aomsweet
 */
public class CarawayApplication {

    private static final LogbackContext logbackContext = new LogbackContext();

    private static CarawayServer caraway;

    private static final Logger logger = LoggerFactory.getLogger(CarawayApplication.class);

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
                caraway.asyncStop(0).whenComplete((v, e) -> logbackContext.stop());
            } else {
                /*
                 Register a signal handler for Ctrl-C that runs the shutdown hooks
                 https://github.com/oracle/graal/issues/465
                 */
                Signal.handle(new Signal("INT"), sig -> System.exit(0));

                Thread shutdownHookThread = new Thread(CarawayApplication::close);
                shutdownHookThread.setName("Caraway shutdown hook");
                Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            }
        });
    }

    public static void close() {
        caraway.close();
        logbackContext.stop();
    }

}
