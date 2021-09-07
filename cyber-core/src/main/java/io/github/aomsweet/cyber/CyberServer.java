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
package io.github.aomsweet.cyber;

import io.github.aomsweet.cyber.http.interceptor.HttpInterceptorManager;
import io.github.aomsweet.cyber.http.mitm.MitmManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author aomsweet
 */
public class CyberServer implements Closeable {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(CyberServer.class);

    HttpInterceptorManager httpInterceptorManager;
    UpstreamProxyManager upstreamProxyManager;
    MitmManager mitmManager;
    SslContext clientSslContext;
    ChannelManager channelManager;
    ProxyAuthenticator proxyAuthenticator;

    SocketAddress actualBoundAddress;
    SocketAddress preBoundAddress;

    int acceptorEventLoopGroupSize;
    boolean holdAcceptorEventLoopGroup;
    EventLoopGroup acceptorEventLoopGroup;
    int workerEventLoopGroupSize;
    boolean holdWorkerEventLoopGroup;
    EventLoopGroup workerEventLoopGroup;

    private CyberServer() {
    }

    public CompletionStage<Channel> start() {
        if (acceptorEventLoopGroup == null) {
            holdAcceptorEventLoopGroup = true;
            acceptorEventLoopGroup = new NioEventLoopGroup(acceptorEventLoopGroupSize,
                threadFactory("Cyber acceptor-"));
        }
        if (workerEventLoopGroup == null) {
            holdWorkerEventLoopGroup = true;
            workerEventLoopGroup = new NioEventLoopGroup(workerEventLoopGroupSize,
                threadFactory("Cyber worker-"));
        }
        return doBind();
    }

    private CompletionStage<Channel> doBind() {
        final long startTimestamp = System.currentTimeMillis();
        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(acceptorEventLoopGroup, workerEventLoopGroup)
            .channel(NioServerSocketChannel.class);
        if (logger.isTraceEnabled()) {
            bootstrap.handler(new LoggingHandler(LogLevel.TRACE));
        }
        PortUnificationServerHandler unificationServerHandler = new PortUnificationServerHandler(this);
        bootstrap.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (logger.isTraceEnabled()) {
                    pipeline.addLast(HandlerNames.LOGGING, new LoggingHandler(LogLevel.TRACE));
                }
                pipeline.addLast(HandlerNames.ROOT, unificationServerHandler);
            }
        });
        CompletableFuture<Channel> channelFuture = new CompletableFuture<>();
        bootstrap.bind(preBoundAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                channelFuture.complete(channel);
                actualBoundAddress = channel.localAddress();
                String address = actualBoundAddress.toString();
                if (address.charAt(0) == '/') {
                    address = address.substring(1);
                }
                logger.info("Cyber started in {}s. Listening on: {}",
                    (System.currentTimeMillis() - startTimestamp) / 1000.0, address);
            } else {
                logger.error("Cyber start failed.", future.cause());
                channelFuture.completeExceptionally(future.cause());
            }
        });
        return channelFuture;
    }

    public ThreadFactory threadFactory(String prefix) {
        AtomicInteger threadSequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + threadSequence.getAndAdd(1));
            return thread;
        };
    }

    public CompletableFuture<Void> stop() throws InterruptedException, ExecutionException {
        return stop(30);
    }

    public CompletableFuture<Void> stop(int timeout) throws InterruptedException, ExecutionException {
        CompletableFuture<Void> future = doStop(timeout);
        future.get();
        return future;
    }

    public CompletableFuture<Void> asyncStop() {
        return asyncStop(30);
    }

    public CompletableFuture<Void> asyncStop(int timeout) {
        return doStop(timeout);
    }

    private CompletableFuture<Void> doStop(int timeout) {
        logger.info("Cyber is stopping...");
        long stopTimestamp = System.currentTimeMillis();
        CompletableFuture<Void> future;
        if (holdAcceptorEventLoopGroup && !(acceptorEventLoopGroup.isShutdown() || acceptorEventLoopGroup.isShuttingDown())) {
            future = shutdownEventLoopGroup(acceptorEventLoopGroup, timeout,
                "Acceptor EventLoopGroup stopped.");
        } else {
            future = new CompletableFuture<>();
            future.complete(null);
        }
        if (holdWorkerEventLoopGroup && !(workerEventLoopGroup.isShutdown() || workerEventLoopGroup.isShuttingDown())) {
            future = future.thenCompose(unused -> shutdownEventLoopGroup(workerEventLoopGroup, timeout,
                "Worker EventLoopGroup stopped."));
        }
        future.whenComplete((v, e) -> {
            if (e == null) {
                logger.info("Cyber stopped in {}s.", (System.currentTimeMillis() - stopTimestamp) / 1000.0);
            } else {
                logger.error("Failed to stop cyber.", e);
            }
        });
        return future;
    }

    private CompletableFuture<Void> shutdownEventLoopGroup(EventLoopGroup eventLoopGroup, int timeout, String comment) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        eventLoopGroup.shutdownGracefully(0, timeout, TimeUnit.SECONDS).addListener(future -> {
            if (future.isSuccess()) {
                logger.info(comment);
                completableFuture.complete(null);
            } else {
                completableFuture.completeExceptionally(future.cause());
            }
        });
        return completableFuture;
    }

    @Override
    public void close() {
        try {
            stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    #####################################################################################
    ################################## Getter | Setter ##################################
    #####################################################################################
     */

    public HttpInterceptorManager getHttpInterceptorManager() {
        return httpInterceptorManager;
    }

    public CyberServer setHttpInterceptorManager(HttpInterceptorManager httpInterceptorManager) {
        this.httpInterceptorManager = httpInterceptorManager;
        return this;
    }

    public UpstreamProxyManager getUpstreamProxyManager() {
        return upstreamProxyManager;
    }

    public CyberServer setUpstreamProxyManager(UpstreamProxyManager upstreamProxyManager) {
        this.upstreamProxyManager = upstreamProxyManager;
        return this;
    }

    public MitmManager getMitmManager() {
        return mitmManager;
    }

    public CyberServer setMitmManager(MitmManager mitmManager) {
        this.mitmManager = mitmManager;
        return this;
    }

    public SslContext getClientSslContext() {
        return clientSslContext;
    }

    public CyberServer setClientSslContext(SslContext clientSslContext) {
        this.clientSslContext = clientSslContext;
        return this;
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public CyberServer setChannelManager(ChannelManager channelManager) {
        this.channelManager = channelManager;
        return this;
    }

    public ProxyAuthenticator getProxyAuthenticator() {
        return proxyAuthenticator;
    }

    public CyberServer setProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
        this.proxyAuthenticator = proxyAuthenticator;
        return this;
    }

    public SocketAddress getActualBoundAddress() {
        return actualBoundAddress;
    }

    public CyberServer setActualBoundAddress(SocketAddress actualBoundAddress) {
        this.actualBoundAddress = actualBoundAddress;
        return this;
    }

    public SocketAddress getPreBoundAddress() {
        return preBoundAddress;
    }

    public CyberServer setPreBoundAddress(SocketAddress preBoundAddress) {
        this.preBoundAddress = preBoundAddress;
        return this;
    }

    public int getAcceptorEventLoopGroupSize() {
        return acceptorEventLoopGroupSize;
    }

    public CyberServer setAcceptorEventLoopGroupSize(int acceptorEventLoopGroupSize) {
        this.acceptorEventLoopGroupSize = acceptorEventLoopGroupSize;
        return this;
    }

    public boolean isHoldAcceptorEventLoopGroup() {
        return holdAcceptorEventLoopGroup;
    }

    public CyberServer setHoldAcceptorEventLoopGroup(boolean holdAcceptorEventLoopGroup) {
        this.holdAcceptorEventLoopGroup = holdAcceptorEventLoopGroup;
        return this;
    }

    public EventLoopGroup getAcceptorEventLoopGroup() {
        return acceptorEventLoopGroup;
    }

    public CyberServer setAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
        this.acceptorEventLoopGroup = acceptorEventLoopGroup;
        return this;
    }

    public int getWorkerEventLoopGroupSize() {
        return workerEventLoopGroupSize;
    }

    public CyberServer setWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
        this.workerEventLoopGroupSize = workerEventLoopGroupSize;
        return this;
    }

    public boolean isHoldWorkerEventLoopGroup() {
        return holdWorkerEventLoopGroup;
    }

    public CyberServer setHoldWorkerEventLoopGroup(boolean holdWorkerEventLoopGroup) {
        this.holdWorkerEventLoopGroup = holdWorkerEventLoopGroup;
        return this;
    }

    public EventLoopGroup getWorkerEventLoopGroup() {
        return workerEventLoopGroup;
    }

    public CyberServer setWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
        this.workerEventLoopGroup = workerEventLoopGroup;
        return this;
    }

    /**
     * Builder
     */
    public static class Builder {

        CyberServer cyber;

        public Builder() {
            cyber = new CyberServer();
        }

        public CyberServer build() {
            if (cyber.getAcceptorEventLoopGroup() == null) {
                cyber.acceptorEventLoopGroupSize = 1;
            }
            if (cyber.getWorkerEventLoopGroup() == null) {
                cyber.workerEventLoopGroupSize = Runtime.getRuntime().availableProcessors();
            }
            if (cyber.channelManager == null) {
                cyber.channelManager = new UnpooledChannelManager();
            }
            if (cyber.preBoundAddress == null) {
                cyber.preBoundAddress = new InetSocketAddress("127.0.0.1", 2228);
            }
            return cyber;
        }

        public Builder withHttpInterceptorManager(HttpInterceptorManager httpInterceptorManager) {
            cyber.httpInterceptorManager = httpInterceptorManager;
            return this;
        }

        public Builder withUpstreamProxyManager(UpstreamProxyManager upstreamProxyManager) {
            cyber.upstreamProxyManager = upstreamProxyManager;
            return this;
        }

        public Builder withMitmManager(MitmManager mitmManager) {
            cyber.mitmManager = mitmManager;
            return this;
        }

        public Builder withClientSslContext(SslContext clientSslContext) {
            cyber.clientSslContext = clientSslContext;
            return this;
        }

        public Builder withChannelManager(ChannelManager channelManager) {
            cyber.channelManager = channelManager;
            return this;
        }

        public Builder withProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
            cyber.proxyAuthenticator = proxyAuthenticator;
            return this;
        }

        public Builder withPort(int port) {
            cyber.preBoundAddress = new InetSocketAddress(port);
            return this;
        }

        public Builder withAddress(String host, int port) {
            cyber.preBoundAddress = new InetSocketAddress(host, port);
            return this;
        }

        public Builder withAddress(SocketAddress address) {
            cyber.preBoundAddress = address;
            return this;
        }

        public Builder withBossEventLoopGroupSize(int bossEventLoopGroupSize) {
            cyber.acceptorEventLoopGroupSize = bossEventLoopGroupSize;
            return this;
        }

        public Builder withAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
            cyber.acceptorEventLoopGroup = acceptorEventLoopGroup;
            return this;
        }

        public Builder withWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
            cyber.workerEventLoopGroupSize = workerEventLoopGroupSize;
            return this;
        }

        public Builder withWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
            cyber.workerEventLoopGroup = workerEventLoopGroup;
            return this;
        }

    }
}
