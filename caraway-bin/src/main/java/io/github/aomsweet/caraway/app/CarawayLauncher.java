package io.github.aomsweet.caraway.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.ProxyType;
import io.github.aomsweet.caraway.app.logback.AnsiConsoleAppender;
import io.github.aomsweet.caraway.app.logback.LogbackConfigurator;
import io.github.aomsweet.caraway.http.mitm.BouncyCastleSelfSignedMitmManager;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * @author aomsweet
 */
public class CarawayLauncher {

    private static final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    private static final Logger logger = LoggerFactory.getLogger(CarawayLauncher.class);

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
        logger.info("Starting Caraway on {} ({})", mx.getName(), System.getProperty("user.dir"));
        CarawayServer caraway = new CarawayServer.Builder()
            // .withProxyAuthenticator(((username, password) -> "admin".equals(username) && "admin".equals(password)))
            // .withUpstreamProxy(() -> new HttpProxyHandler(new InetSocketAddress("localhost", 7890)))
            .withUpstreamProxy(ProxyType.SOCKS5, "127.0.0.1", 7890)
            .withSocks5ChainedProxyManager((request, credentials, clientAddress, serverAddress) -> {
                Queue<Supplier<ProxyHandler>> queue = new ArrayDeque<>();
                queue.offer(() -> new HttpProxyHandler(new InetSocketAddress("127.0.0.1", 7891)));
                // queue.offer(() -> new HttpProxyHandler(new InetSocketAddress("127.0.0.1", 7892)));
                queue.offer(() -> new HttpProxyHandler(new InetSocketAddress("127.0.0.1", 7890)));
                return queue;
            })
            .withMitmManager(new BouncyCastleSelfSignedMitmManager())
            .withPort(2228)
            .build();
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
        AnsiConsoleAppender<ILoggingEvent> consoleAppender = ((AnsiConsoleAppender<ILoggingEvent>) rootLogger
            .getAppender(AnsiConsoleAppender.DEFAULT_NAME));

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
