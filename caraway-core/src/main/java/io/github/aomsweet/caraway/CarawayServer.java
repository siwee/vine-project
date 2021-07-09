package io.github.aomsweet.caraway;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author aomsweet
 */
public class CarawayServer implements Closeable {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(CarawayServer.class);

    MitmManager mitmManager;
    SslContext clientSslContext;
    ServerConnector connector;
    ProxyAuthenticator proxyAuthenticator;
    SocketAddress actualBoundAddress;
    SocketAddress preBoundAddress;
    int bossEventLoopGroupSize;
    boolean holdBossEventLoopGroup;
    EventLoopGroup acceptorEventLoopGroup;
    int workerEventLoopGroupSize;
    boolean holdWorkerEventLoopGroup;
    EventLoopGroup workerEventLoopGroup;
    long startTimestamp;

    public CarawayServer() {
        this.bossEventLoopGroupSize = 1;
        this.workerEventLoopGroupSize = Runtime.getRuntime().availableProcessors();
        this.connector = new DirectServerConnector();
    }

    public CompletionStage<Channel> start() {
        startTimestamp = System.currentTimeMillis();
        if (acceptorEventLoopGroup == null) {
            holdBossEventLoopGroup = true;
            acceptorEventLoopGroup = new NioEventLoopGroup(bossEventLoopGroupSize,
                threadFactory("Caraway acceptor-"));
        }
        if (workerEventLoopGroup == null) {
            holdWorkerEventLoopGroup = true;
            workerEventLoopGroup = new NioEventLoopGroup(workerEventLoopGroupSize,
                threadFactory("Caraway worker-"));
        }
        return doBind();
    }

    private CompletionStage<Channel> doBind() {
        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(acceptorEventLoopGroup, workerEventLoopGroup)
            .channel(NioServerSocketChannel.class);
        if (logger.isTraceEnabled()) {
            bootstrap.handler(new LoggingHandler(LogLevel.TRACE));
        }
        PortUnificationServerHandler unificationServerHandler = new PortUnificationServerHandler(this);
        bootstrap.childHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (logger.isTraceEnabled()) {
                    pipeline.addLast(new LoggingHandler(LogLevel.TRACE));
                }
                pipeline.addLast(unificationServerHandler);
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
                logger.info("Caraway started in {}s. Listening on: {}",
                    (System.currentTimeMillis() - startTimestamp) / 1000.0, address);
            } else {
                startTimestamp = 0;
                logger.error("Caraway start failed.", future.cause());
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
        logger.info("Caraway is stopping...");
        long stopTimestamp = System.currentTimeMillis();
        CompletableFuture<Void> future;
        if (holdBossEventLoopGroup && !(acceptorEventLoopGroup.isShutdown() || acceptorEventLoopGroup.isShuttingDown())) {
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
                logger.info("Caraway stopped in {}s.", (System.currentTimeMillis() - stopTimestamp) / 1000.0);
            } else {
                logger.error("Failed to stop caraway.", e);
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

    public CarawayServer withMitmManager(MitmManager mitmManager) {
        this.mitmManager = mitmManager;
        return this;
    }

    public CarawayServer withClientSslContext(SslContext clientSslContext) {
        this.clientSslContext = clientSslContext;
        return this;
    }

    public CarawayServer withServerConnector(ServerConnector connector) {
        this.connector = connector;
        return this;
    }

    public CarawayServer withProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
        this.proxyAuthenticator = proxyAuthenticator;
        return this;
    }

    public CarawayServer withPort(int port) {
        this.preBoundAddress = new InetSocketAddress(port);
        return this;
    }

    public CarawayServer withAddress(String host, int port) {
        this.preBoundAddress = new InetSocketAddress(host, port);
        return this;
    }

    public CarawayServer withAddress(SocketAddress address) {
        this.preBoundAddress = address;
        return this;
    }

    public CarawayServer withBossEventLoopGroupSize(int bossEventLoopGroupSize) {
        this.bossEventLoopGroupSize = bossEventLoopGroupSize;
        return this;
    }

    public CarawayServer withAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
        this.acceptorEventLoopGroup = acceptorEventLoopGroup;
        return this;
    }

    public CarawayServer withWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
        this.workerEventLoopGroupSize = workerEventLoopGroupSize;
        return this;
    }

    public CarawayServer withWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
        this.workerEventLoopGroup = workerEventLoopGroup;
        return this;
    }

    public MitmManager getMitmManager() {
        return mitmManager;
    }

    public SslContext getClientSslContext() throws SSLException {
        if (clientSslContext == null) {
            synchronized (this) {
                if (clientSslContext == null) {
                    //https://github.com/GlowstoneMC/Glowstone/blob/5b89f945b4/src/main/java/net/glowstone/net/http/HttpClient.java
                    clientSslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                }
            }
        }
        return clientSslContext;
    }

    public ServerConnector getConnector() {
        return connector;
    }

    public ProxyAuthenticator getProxyAuthenticator() {
        return proxyAuthenticator;
    }

    public SocketAddress getActualBoundAddress() {
        return actualBoundAddress;
    }

    public SocketAddress getPreBoundAddress() {
        return preBoundAddress;
    }

    public int getBossEventLoopGroupSize() {
        return bossEventLoopGroupSize;
    }

    public boolean isHoldBossEventLoopGroup() {
        return holdBossEventLoopGroup;
    }

    public EventLoopGroup getAcceptorEventLoopGroup() {
        return acceptorEventLoopGroup;
    }

    public int getWorkerEventLoopGroupSize() {
        return workerEventLoopGroupSize;
    }

    public boolean isHoldWorkerEventLoopGroup() {
        return holdWorkerEventLoopGroup;
    }

    public EventLoopGroup getWorkerEventLoopGroup() {
        return workerEventLoopGroup;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    @Override
    public void close() {
        try {
            stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
