package io.github.aomsweet.caraway.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.app.logback.ConsoleAppender;
import io.github.aomsweet.caraway.app.logback.LogbackConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * @author aomsweet
 */
public class CarawayLauncher {

    private static final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    private static final Logger logger = LoggerFactory.getLogger(CarawayLauncher.class);

    public static void main(String[] args) {
        /*
         Register a signal handler for Ctrl-C that runs the shutdown hooks
         https://github.com/oracle/graal/issues/465
         */
        Signal.handle(new Signal("INT"), s -> System.exit(0));
        if (LogbackConfigurator.isEnabled()) {
            logbackConfigure();
        }
        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        logger.info("Starting Caraway on {} ({})", mx.getName(), System.getProperty("user.dir"));
        CarawayServer caraway = new CarawayServer()
            .withPort(2228);
        caraway.start().whenComplete((channel, cause) -> {
            if (channel == null) {
                caraway.asyncStop(0).whenComplete((v, e) -> loggerContext.stop());
            } else {
                Thread shutdownHookThread = new Thread(() -> close(caraway));
                shutdownHookThread.setName("Caraway shutdown hook");
                Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            }
        });
    }

    public static void close(CarawayServer caraway) {
        caraway.close();
        loggerContext.stop();
    }

    public static void logbackConfigure() {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        ConsoleAppender<ILoggingEvent> consoleAppender = ((ConsoleAppender<ILoggingEvent>) rootLogger
            .getAppender(ConsoleAppender.DEFAULT_NAME));

        String pattern = "%d{yyyy-MM-dd HH:mm:ss:SSS} | %highlight(%-5level) %green([%thread]) %boldMagenta(%logger{36}) - %cyan(%msg %n)";

        PatternLayout layout = new PatternLayout();
        layout.setPattern(pattern);
        layout.setContext(loggerContext);
        layout.start();

        LayoutWrappingEncoder<ILoggingEvent> encoder = (LayoutWrappingEncoder<ILoggingEvent>) consoleAppender.getEncoder();
        encoder.setLayout(layout);

        consoleAppender.setWithJansi(true);
        consoleAppender.start();
    }

}
