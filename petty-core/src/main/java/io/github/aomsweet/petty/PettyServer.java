package io.github.aomsweet.petty;

import io.github.aomsweet.petty.http.mitm.MitmManager;
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
public class PettyServer implements Closeable {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(PettyServer.class);

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

    private PettyServer() {
    }

    public CompletionStage<Channel> start() {
        if (acceptorEventLoopGroup == null) {
            holdAcceptorEventLoopGroup = true;
            acceptorEventLoopGroup = new NioEventLoopGroup(acceptorEventLoopGroupSize,
                threadFactory("Petty acceptor-"));
        }
        if (workerEventLoopGroup == null) {
            holdWorkerEventLoopGroup = true;
            workerEventLoopGroup = new NioEventLoopGroup(workerEventLoopGroupSize,
                threadFactory("Petty worker-"));
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
                logger.info("Petty started in {}s. Listening on: {}",
                    (System.currentTimeMillis() - startTimestamp) / 1000.0, address);
            } else {
                logger.error("Petty start failed.", future.cause());
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
        logger.info("Petty is stopping...");
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
                logger.info("Petty stopped in {}s.", (System.currentTimeMillis() - stopTimestamp) / 1000.0);
            } else {
                logger.error("Failed to stop petty.", e);
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

    public PettyServer setHttpChainedProxyManager(ChainedProxyManager<HttpRequest> httpChainedProxyManager) {
        this.httpChainedProxyManager = httpChainedProxyManager;
        return this;
    }

    public ChainedProxyManager<Socks4CommandRequest> getSocks4ChainedProxyManager() {
        return socks4ChainedProxyManager;
    }

    public PettyServer setSocks4ChainedProxyManager(ChainedProxyManager<Socks4CommandRequest> socks4ChainedProxyManager) {
        this.socks4ChainedProxyManager = socks4ChainedProxyManager;
        return this;
    }

    public ChainedProxyManager<Socks5CommandRequest> getSocks5ChainedProxyManager() {
        return socks5ChainedProxyManager;
    }

    public PettyServer setSocks5ChainedProxyManager(ChainedProxyManager<Socks5CommandRequest> socks5ChainedProxyManager) {
        this.socks5ChainedProxyManager = socks5ChainedProxyManager;
        return this;
    }

    public MitmManager getMitmManager() {
        return mitmManager;
    }

    public PettyServer setMitmManager(MitmManager mitmManager) {
        this.mitmManager = mitmManager;
        return this;
    }

    public SslContext getClientSslContext() {
        return clientSslContext;
    }

    public PettyServer setClientSslContext(SslContext clientSslContext) {
        this.clientSslContext = clientSslContext;
        return this;
    }

    public ServerConnector getConnector() {
        return connector;
    }

    public PettyServer setConnector(ServerConnector connector) {
        this.connector = connector;
        return this;
    }

    public ProxyAuthenticator getProxyAuthenticator() {
        return proxyAuthenticator;
    }

    public PettyServer setProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
        this.proxyAuthenticator = proxyAuthenticator;
        return this;
    }

    public SocketAddress getActualBoundAddress() {
        return actualBoundAddress;
    }

    public PettyServer setActualBoundAddress(SocketAddress actualBoundAddress) {
        this.actualBoundAddress = actualBoundAddress;
        return this;
    }

    public SocketAddress getPreBoundAddress() {
        return preBoundAddress;
    }

    public PettyServer setPreBoundAddress(SocketAddress preBoundAddress) {
        this.preBoundAddress = preBoundAddress;
        return this;
    }

    public int getAcceptorEventLoopGroupSize() {
        return acceptorEventLoopGroupSize;
    }

    public PettyServer setAcceptorEventLoopGroupSize(int acceptorEventLoopGroupSize) {
        this.acceptorEventLoopGroupSize = acceptorEventLoopGroupSize;
        return this;
    }

    public boolean isHoldAcceptorEventLoopGroup() {
        return holdAcceptorEventLoopGroup;
    }

    public PettyServer setHoldAcceptorEventLoopGroup(boolean holdAcceptorEventLoopGroup) {
        this.holdAcceptorEventLoopGroup = holdAcceptorEventLoopGroup;
        return this;
    }

    public EventLoopGroup getAcceptorEventLoopGroup() {
        return acceptorEventLoopGroup;
    }

    public PettyServer setAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
        this.acceptorEventLoopGroup = acceptorEventLoopGroup;
        return this;
    }

    public int getWorkerEventLoopGroupSize() {
        return workerEventLoopGroupSize;
    }

    public PettyServer setWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
        this.workerEventLoopGroupSize = workerEventLoopGroupSize;
        return this;
    }

    public boolean isHoldWorkerEventLoopGroup() {
        return holdWorkerEventLoopGroup;
    }

    public PettyServer setHoldWorkerEventLoopGroup(boolean holdWorkerEventLoopGroup) {
        this.holdWorkerEventLoopGroup = holdWorkerEventLoopGroup;
        return this;
    }

    public EventLoopGroup getWorkerEventLoopGroup() {
        return workerEventLoopGroup;
    }

    public PettyServer setWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
        this.workerEventLoopGroup = workerEventLoopGroup;
        return this;
    }

    /**
     * Builder
     */
    public static class Builder {

        PettyServer petty;
        Supplier<ProxyHandler> proxyHandler;

        public Builder() {
            petty = new PettyServer();
        }

        public PettyServer build() {
            if (petty.getAcceptorEventLoopGroup() == null) {
                petty.acceptorEventLoopGroupSize = 1;
            }
            if (petty.getWorkerEventLoopGroup() == null) {
                petty.workerEventLoopGroupSize = Runtime.getRuntime().availableProcessors();
            }
            if (petty.connector == null) {
                petty.connector = new DirectServerConnector();
            }
            if (proxyHandler != null) {
                petty.connector.switchUpstreamProxy(proxyHandler);
            }
            if (petty.preBoundAddress == null) {
                petty.preBoundAddress = new InetSocketAddress("127.0.0.1", 2228);
            }
            return petty;
        }

        public Builder withHttpChainedProxyManager(ChainedProxyManager<HttpRequest> httpChainedProxyManager) {
            petty.httpChainedProxyManager = httpChainedProxyManager;
            return this;
        }

        public Builder withSocks4ChainedProxyManager(ChainedProxyManager<Socks4CommandRequest> socks4ChainedProxyManager) {
            petty.socks4ChainedProxyManager = socks4ChainedProxyManager;
            return this;
        }

        public Builder withSocks5ChainedProxyManager(ChainedProxyManager<Socks5CommandRequest> socks5ChainedProxyManager) {
            petty.socks5ChainedProxyManager = socks5ChainedProxyManager;
            return this;
        }


        public Builder withMitmManager(MitmManager mitmManager) {
            petty.mitmManager = mitmManager;
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
            petty.clientSslContext = clientSslContext;
            return this;
        }

        public Builder withServerConnector(ServerConnector connector) {
            petty.connector = connector;
            return this;
        }

        public Builder withProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
            petty.proxyAuthenticator = proxyAuthenticator;
            return this;
        }

        public Builder withPort(int port) {
            petty.preBoundAddress = new InetSocketAddress(port);
            return this;
        }

        public Builder withAddress(String host, int port) {
            petty.preBoundAddress = new InetSocketAddress(host, port);
            return this;
        }

        public Builder withAddress(SocketAddress address) {
            petty.preBoundAddress = address;
            return this;
        }

        public Builder withBossEventLoopGroupSize(int bossEventLoopGroupSize) {
            petty.acceptorEventLoopGroupSize = bossEventLoopGroupSize;
            return this;
        }

        public Builder withAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
            petty.acceptorEventLoopGroup = acceptorEventLoopGroup;
            return this;
        }

        public Builder withWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
            petty.workerEventLoopGroupSize = workerEventLoopGroupSize;
            return this;
        }

        public Builder withWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
            petty.workerEventLoopGroup = workerEventLoopGroup;
            return this;
        }

    }
}
