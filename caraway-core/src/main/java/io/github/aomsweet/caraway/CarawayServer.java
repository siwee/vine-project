package io.github.aomsweet.caraway;

import io.github.aomsweet.caraway.http.mitm.MitmManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author aomsweet
 */
public class CarawayServer implements Closeable {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(CarawayServer.class);

    ChainedProxyManager<HttpRequest> httpChainedProxyManager;
    ChainedProxyManager<Socks4CommandRequest> socks4ChainedProxyManager;
    ChainedProxyManager<Socks5CommandRequest> socks5ChainedProxyManager;
    MitmManager mitmManager;
    SslContext clientSslContext;
    ServerConnector connector;
    ProxyAuthenticator proxyAuthenticator;

    SocketAddress actualBoundAddress;
    SocketAddress preBoundAddress;

    int acceptorEventLoopGroupSize;
    boolean holdAcceptorEventLoopGroup;
    EventLoopGroup acceptorEventLoopGroup;
    int workerEventLoopGroupSize;
    boolean holdWorkerEventLoopGroup;
    EventLoopGroup workerEventLoopGroup;

    private CarawayServer() {
    }

    public CompletionStage<Channel> start() {
        if (acceptorEventLoopGroup == null) {
            holdAcceptorEventLoopGroup = true;
            acceptorEventLoopGroup = new NioEventLoopGroup(acceptorEventLoopGroupSize,
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
        final long startTimestamp = System.currentTimeMillis();
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

    public ChainedProxyManager<HttpRequest> getHttpChainedProxyManager() {
        return httpChainedProxyManager;
    }

    public CarawayServer setHttpChainedProxyManager(ChainedProxyManager<HttpRequest> httpChainedProxyManager) {
        this.httpChainedProxyManager = httpChainedProxyManager;
        return this;
    }

    public ChainedProxyManager<Socks4CommandRequest> getSocks4ChainedProxyManager() {
        return socks4ChainedProxyManager;
    }

    public CarawayServer setSocks4ChainedProxyManager(ChainedProxyManager<Socks4CommandRequest> socks4ChainedProxyManager) {
        this.socks4ChainedProxyManager = socks4ChainedProxyManager;
        return this;
    }

    public ChainedProxyManager<Socks5CommandRequest> getSocks5ChainedProxyManager() {
        return socks5ChainedProxyManager;
    }

    public CarawayServer setSocks5ChainedProxyManager(ChainedProxyManager<Socks5CommandRequest> socks5ChainedProxyManager) {
        this.socks5ChainedProxyManager = socks5ChainedProxyManager;
        return this;
    }

    public MitmManager getMitmManager() {
        return mitmManager;
    }

    public CarawayServer setMitmManager(MitmManager mitmManager) {
        this.mitmManager = mitmManager;
        return this;
    }

    public SslContext getClientSslContext() {
        return clientSslContext;
    }

    public CarawayServer setClientSslContext(SslContext clientSslContext) {
        this.clientSslContext = clientSslContext;
        return this;
    }

    public ServerConnector getConnector() {
        return connector;
    }

    public CarawayServer setConnector(ServerConnector connector) {
        this.connector = connector;
        return this;
    }

    public ProxyAuthenticator getProxyAuthenticator() {
        return proxyAuthenticator;
    }

    public CarawayServer setProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
        this.proxyAuthenticator = proxyAuthenticator;
        return this;
    }

    public SocketAddress getActualBoundAddress() {
        return actualBoundAddress;
    }

    public CarawayServer setActualBoundAddress(SocketAddress actualBoundAddress) {
        this.actualBoundAddress = actualBoundAddress;
        return this;
    }

    public SocketAddress getPreBoundAddress() {
        return preBoundAddress;
    }

    public CarawayServer setPreBoundAddress(SocketAddress preBoundAddress) {
        this.preBoundAddress = preBoundAddress;
        return this;
    }

    public int getAcceptorEventLoopGroupSize() {
        return acceptorEventLoopGroupSize;
    }

    public CarawayServer setAcceptorEventLoopGroupSize(int acceptorEventLoopGroupSize) {
        this.acceptorEventLoopGroupSize = acceptorEventLoopGroupSize;
        return this;
    }

    public boolean isHoldAcceptorEventLoopGroup() {
        return holdAcceptorEventLoopGroup;
    }

    public CarawayServer setHoldAcceptorEventLoopGroup(boolean holdAcceptorEventLoopGroup) {
        this.holdAcceptorEventLoopGroup = holdAcceptorEventLoopGroup;
        return this;
    }

    public EventLoopGroup getAcceptorEventLoopGroup() {
        return acceptorEventLoopGroup;
    }

    public CarawayServer setAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
        this.acceptorEventLoopGroup = acceptorEventLoopGroup;
        return this;
    }

    public int getWorkerEventLoopGroupSize() {
        return workerEventLoopGroupSize;
    }

    public CarawayServer setWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
        this.workerEventLoopGroupSize = workerEventLoopGroupSize;
        return this;
    }

    public boolean isHoldWorkerEventLoopGroup() {
        return holdWorkerEventLoopGroup;
    }

    public CarawayServer setHoldWorkerEventLoopGroup(boolean holdWorkerEventLoopGroup) {
        this.holdWorkerEventLoopGroup = holdWorkerEventLoopGroup;
        return this;
    }

    public EventLoopGroup getWorkerEventLoopGroup() {
        return workerEventLoopGroup;
    }

    public CarawayServer setWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
        this.workerEventLoopGroup = workerEventLoopGroup;
        return this;
    }

    /**
     * Builder
     */
    public static class Builder {

        CarawayServer caraway;
        Supplier<ProxyHandler> proxyHandler;

        public Builder() {
            caraway = new CarawayServer();
        }

        public CarawayServer build() {
            if (caraway.getAcceptorEventLoopGroup() == null) {
                caraway.acceptorEventLoopGroupSize = 1;
            }
            if (caraway.getWorkerEventLoopGroup() == null) {
                caraway.workerEventLoopGroupSize = Runtime.getRuntime().availableProcessors();
            }
            if (caraway.connector == null) {
                caraway.connector = new DirectServerConnector();
            }
            if (proxyHandler != null) {
                caraway.connector.switchUpstreamProxy(proxyHandler);
            }
            if (caraway.preBoundAddress == null) {
                caraway.preBoundAddress = new InetSocketAddress("127.0.0.1", 2228);
            }
            return caraway;
        }

        public Builder withHttpChainedProxyManager(ChainedProxyManager<HttpRequest> httpChainedProxyManager) {
            caraway.httpChainedProxyManager = httpChainedProxyManager;
            return this;
        }

        public Builder withSocks4ChainedProxyManager(ChainedProxyManager<Socks4CommandRequest> socks4ChainedProxyManager) {
            caraway.socks4ChainedProxyManager = socks4ChainedProxyManager;
            return this;
        }

        public Builder withSocks5ChainedProxyManager(ChainedProxyManager<Socks5CommandRequest> socks5ChainedProxyManager) {
            caraway.socks5ChainedProxyManager = socks5ChainedProxyManager;
            return this;
        }


        public Builder withMitmManager(MitmManager mitmManager) {
            caraway.mitmManager = mitmManager;
            return this;
        }

        public Builder withUpstreamProxy(Supplier<ProxyHandler> proxyHandler) {
            this.proxyHandler = proxyHandler;
            return this;
        }

        public Builder withUpstreamProxy(ProxyType type, String host, int port) {
            return withUpstreamProxy(type, host, port, null, null);
        }

        public Builder withUpstreamProxy(ProxyType type, String host, int port, String username, String password) {
            Supplier<ProxyHandler> proxyHandler;
            switch (type) {
                case SOCKS5:
                    proxyHandler = () -> new Socks5ProxyHandler(new InetSocketAddress(host, port), username, password);
                    break;
                case SOCKS4a:
                    proxyHandler = () -> new Socks4ProxyHandler(new InetSocketAddress(host, port));
                    break;
                case HTTP:
                    proxyHandler = () -> new HttpProxyHandler(new InetSocketAddress(host, port), username, password);
                    break;
                default:
                    throw new RuntimeException("UnKnow proxy type.");
            }
            this.proxyHandler = proxyHandler;
            return this;
        }


        public Builder withClientSslContext(SslContext clientSslContext) {
            caraway.clientSslContext = clientSslContext;
            return this;
        }

        public Builder withServerConnector(ServerConnector connector) {
            caraway.connector = connector;
            return this;
        }

        public Builder withProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
            caraway.proxyAuthenticator = proxyAuthenticator;
            return this;
        }

        public Builder withPort(int port) {
            caraway.preBoundAddress = new InetSocketAddress(port);
            return this;
        }

        public Builder withAddress(String host, int port) {
            caraway.preBoundAddress = new InetSocketAddress(host, port);
            return this;
        }

        public Builder withAddress(SocketAddress address) {
            caraway.preBoundAddress = address;
            return this;
        }

        public Builder withBossEventLoopGroupSize(int bossEventLoopGroupSize) {
            caraway.acceptorEventLoopGroupSize = bossEventLoopGroupSize;
            return this;
        }

        public Builder withAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
            caraway.acceptorEventLoopGroup = acceptorEventLoopGroup;
            return this;
        }

        public Builder withWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
            caraway.workerEventLoopGroupSize = workerEventLoopGroupSize;
            return this;
        }

        public Builder withWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
            caraway.workerEventLoopGroup = workerEventLoopGroup;
            return this;
        }

    }
}
