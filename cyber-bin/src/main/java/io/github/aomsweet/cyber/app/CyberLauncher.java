/*
  Copyright 2021 The Cyber Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.github.aomsweet.cyber.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import io.github.aomsweet.cyber.CyberServer;
import io.github.aomsweet.cyber.app.logback.AnsiConsoleAppender;
import io.github.aomsweet.cyber.app.logback.LogbackConfigurator;
import io.github.aomsweet.cyber.http.interceptor.FullHttpRequestInterceptor;
import io.github.aomsweet.cyber.http.interceptor.FullHttpResponseInterceptor;
import io.github.aomsweet.cyber.http.interceptor.HttpInterceptorManager;
import io.github.aomsweet.cyber.http.mitm.BouncyCastleSelfSignedMitmManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * @author aomsweet
 */
public class CyberLauncher {

    private static final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    private static final Logger logger = LoggerFactory.getLogger(CyberLauncher.class);

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
        logger.info("Starting Cyber on {} ({})", mx.getName(), System.getProperty("user.dir"));
        CyberServer cyber = new CyberServer.Builder()
            // .withProxyAuthenticator(((username, password) -> "admin".equals(username) && "admin".equals(password)))
            // .withUpstreamProxy(() -> new HttpProxyHandler(new InetSocketAddress("localhost", 7890)))
            // .withUpstreamProxy(ProxyType.SOCKS5, "127.0.0.1", 7890)
            // .withUpstreamProxyManager((request, credentials, clientAddress, serverAddress) ->
            //     List.of(new ProxyInfo(ProxyType.HTTP, "127.0.0.1", 8888)))
            .withMitmManager(new BouncyCastleSelfSignedMitmManager())
            .withHttpInterceptorManager(new HttpInterceptorManager()
                .addInterceptor(new FullHttpRequestInterceptor() {
                    @Override
                    public boolean preHandle(Channel clientChannel, FullHttpRequest httpRequest) throws Exception {
                        httpRequest.headers().add("Cyber", "for test.");
                        return true;
                    }
                })
                .addInterceptor(new FullHttpResponseInterceptor() {
                    @Override
                    public boolean preHandle(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, FullHttpResponse httpResponse) throws Exception {
                        httpResponse.headers().add("Cyber", "for test.");
                        return true;
                    }
                })
            )
            .withPort(2228)
            .build();
        cyber.start().whenComplete((channel, cause) -> {
            if (channel == null) {
                cyber.asyncStop(0).whenComplete((v, e) -> loggerContext.stop());
            } else {
                Thread shutdownHookThread = new Thread(() -> close(cyber));
                shutdownHookThread.setName("Cyber shutdown hook");
                Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            }
        });
    }

    public static void close(CyberServer cyber) {
        cyber.close();
        loggerContext.stop();
    }

    public static void logbackConfigure() {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        AnsiConsoleAppender<ILoggingEvent> consoleAppender = ((AnsiConsoleAppender<ILoggingEvent>) rootLogger
            .getAppender(AnsiConsoleAppender.DEFAULT_NAME));

        String pattern = "%d{yyyy-MM-dd HH:mm:ss:SSS} | %highlight(%-5level) %green([%thread]) %boldMagenta(%logger{36}) - %cyan(%msg) %n%boldRed(%ex)";

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
