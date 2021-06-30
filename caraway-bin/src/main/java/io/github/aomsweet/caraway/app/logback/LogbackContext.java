package io.github.aomsweet.caraway.app.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import org.slf4j.LoggerFactory;

/**
 * @author aomsweet
 */
public class LogbackContext {

    private final LoggerContext logbackContext;

    public LogbackContext() {
        this.logbackContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        configure();
    }

    public void configure() {
        logbackContext.stop();

        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(logbackContext);
        consoleAppender.setName("console");

        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(logbackContext);

        String pattern = "%d{MM-dd HH:mm:ss:SSS} | %highlight(%-5level) %green([%thread]) %boldMagenta(%logger{36}) - %cyan(%msg %n)";

        PatternLayout layout = new PatternLayout();
        layout.setPattern(pattern);
        layout.setContext(logbackContext);
        layout.start();

        encoder.setLayout(layout);

        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        Logger rootLogger = logbackContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(consoleAppender);

        logbackContext.setPackagingDataEnabled(true);
        if (!logbackContext.isStarted()) {
            logbackContext.start();
        }
    }

    public void stop() {
        logbackContext.stop();
    }
}
