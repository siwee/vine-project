package io.github.aomsweet.caraway;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
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
public class CarawayServer implements Closeable {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(CarawayServer.class);

    ProxyConnector connector;
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
        this.connector = new DirectProxyConnector();
    }

    public CompletionStage<Channel> start() {
        startTimestamp = System.currentTimeMillis();
        if (acceptorEventLoopGroup == null) {
            holdBossEventLoopGroup = true;
            acceptorEventLoopGroup = new NioEventLoopGroup(bossEventLoopGroupSize,
                threadFactory("Caraway NIO acceptor thread-"));
        }
        if (workerEventLoopGroup == null) {
            holdWorkerEventLoopGroup = true;
            workerEventLoopGroup = new NioEventLoopGroup(workerEventLoopGroupSize,
                threadFactory("Caraway NIO worker thread-"));
        }
        return doBind();
    }

    private CompletionStage<Channel> doBind() {
        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(acceptorEventLoopGroup, workerEventLoopGroup)
            .channel(NioServerSocketChannel.class);
        if (logger.isDebugEnabled()) {
            bootstrap.handler(new LoggingHandler());
        }
        PortUnificationServerHandler unificationServerHandler = new PortUnificationServerHandler(this);
        bootstrap.childHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (logger.isDebugEnabled()) {
                    pipeline.addLast(new LoggingHandler());
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
        return stop(false);
    }

    public CompletableFuture<Void> stop(boolean immediately) throws InterruptedException, ExecutionException {
        CompletableFuture<Void> future = doStop(immediately);
        future.get();
        return future;
    }

    public CompletableFuture<Void> asyncStop() {
        return asyncStop(false);
    }

    public CompletableFuture<Void> asyncStop(boolean immediately) {
        return doStop(immediately);
    }

    private CompletableFuture<Void> doStop(boolean immediately) {
        long stopTimestamp = System.currentTimeMillis();
        CompletableFuture<Void> future;
        if (holdBossEventLoopGroup && !(acceptorEventLoopGroup.isShutdown() || acceptorEventLoopGroup.isShuttingDown())) {
            future = shutdownEventLoopGroup(acceptorEventLoopGroup, immediately, "Shutdown acceptor EventLoopGroup.");
        } else {
            future = new CompletableFuture<>();
            future.complete(null);
        }
        if (holdWorkerEventLoopGroup && !(workerEventLoopGroup.isShutdown() || workerEventLoopGroup.isShuttingDown())) {
            future = future.thenCompose(unused -> shutdownEventLoopGroup(workerEventLoopGroup, immediately, "Shutdown worker EventLoopGroup."));
        }
        future.whenComplete((v, e) -> {
            if (e == null) {
                logger.info("Caraway stopped in {}s.", (System.currentTimeMillis() - stopTimestamp) / 1000.0);
            } else {
                logger.error("Failed to close caraway.", e);
            }
        });
        return future;
    }

    private CompletableFuture<Void> shutdownEventLoopGroup(EventLoopGroup eventLoopGroup, boolean immediately, String comment) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        GenericFutureListener<Future<Object>> futureListener = future -> {
            if (future.isSuccess()) {
                logger.info(comment);
                completableFuture.complete(null);
            } else {
                completableFuture.completeExceptionally(future.cause());
            }
        };
        if (immediately) {
            eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).addListener(futureListener);
        } else {
            eventLoopGroup.shutdownGracefully().addListener(futureListener);
        }
        return completableFuture;
    }

    public CarawayServer withConnector(ProxyConnector connector) {
        this.connector = connector;
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

    public ProxyConnector getConnector() {
        return connector;
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
