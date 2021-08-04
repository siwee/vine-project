package io.github.aomsweet.petty.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import io.github.aomsweet.petty.PettyServer;
import io.github.aomsweet.petty.ProxyInfo;
import io.github.aomsweet.petty.ProxyType;
import io.github.aomsweet.petty.app.logback.AnsiConsoleAppender;
import io.github.aomsweet.petty.app.logback.LogbackConfigurator;
import io.github.aomsweet.petty.http.mitm.BouncyCastleSelfSignedMitmManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

/**
 * @author aomsweet
 */
public class PettyLauncher {

    private static final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    private static final Logger logger = LoggerFactory.getLogger(PettyLauncher.class);

    public static void main(String[] args) throws Exception {
        /*
         Register a signal handler for Ctrl-C that runs the shutdown hooks
         https://github.com/oracle/graal/issues/465
         */
        Signal.handle(new Signal("INT"), s -> System.exit(0));
        if (LogbackConfigurator.isEnabled()) {
            logbackConfigure();
        }
        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        logger.info("Starting Petty on {} ({})", mx.getName(), System.getProperty("user.dir"));
        PettyServer petty = new PettyServer.Builder()
            // .withProxyAuthenticator(((username, password) -> "admin".equals(username) && "admin".equals(password)))
            // .withUpstreamProxy(() -> new HttpProxyHandler(new InetSocketAddress("localhost", 7890)))
            // .withUpstreamProxy(ProxyType.SOCKS5, "127.0.0.1", 7890)
            .withUpstreamProxyManager((request, credentials, clientAddress, serverAddress) ->
                List.of(new ProxyInfo(ProxyType.HTTP, "127.0.0.1", 8888)))
            .withMitmManager(new BouncyCastleSelfSignedMitmManager())
            .withPort(2228)
            .build();
        petty.start().whenComplete((channel, cause) -> {
            if (channel == null) {
                petty.asyncStop(0).whenComplete((v, e) -> loggerContext.stop());
            } else {
                Thread shutdownHookThread = new Thread(() -> close(petty));
                shutdownHookThread.setName("Petty shutdown hook");
                Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            }
        });
    }

    public static void close(PettyServer petty) {
        petty.close();
        loggerContext.stop();
    }

    public static void logbackConfigure() {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        AnsiConsoleAppender<ILoggingEvent> consoleAppender = ((AnsiConsoleAppender<ILoggingEvent>) rootLogger
            .getAppender(AnsiConsoleAppender.DEFAULT_NAME));

        String pattern = "%d{yyyy-MM-dd HH:mm:ss:SSS} | %highlight(%-5level) %green([%thread]) %boldMagenta(%logger{36}) - %cyan(%msg) %n%red(%ex)";

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
