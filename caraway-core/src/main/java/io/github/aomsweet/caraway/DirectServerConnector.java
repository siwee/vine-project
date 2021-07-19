package io.github.aomsweet.caraway;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * @author aomsweet
 */
public class DirectServerConnector implements ServerConnector {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(DirectServerConnector.class);

    Bootstrap bootstrap;
    Supplier<ProxyHandler> upstreamProxySupplier;

    public DirectServerConnector() {
        this.bootstrap = bootstrap();
    }

    public DirectServerConnector(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public DirectServerConnector(Supplier<ProxyHandler> upstreamProxySupplier) {
        this.bootstrap = bootstrap();
        this.upstreamProxySupplier = upstreamProxySupplier;
    }

    public DirectServerConnector(Bootstrap bootstrap, Supplier<ProxyHandler> upstreamProxySupplier) {
        this.bootstrap = bootstrap;
        this.upstreamProxySupplier = upstreamProxySupplier;
    }

    public Bootstrap bootstrap() {
        return bootstrap(channelInitializer());
    }

    public Bootstrap bootstrap(ChannelInitializer<Channel> channelInitializer) {
        return new Bootstrap()
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(channelInitializer);
    }

    public ChannelInitializer<Channel> channelInitializer() {
        return channelInitializer(upstreamProxySupplier);
    }

    public ChannelInitializer<Channel> channelInitializer(Supplier<ProxyHandler> proxyHandler) {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                if (logger.isTraceEnabled()) {
                    ch.pipeline().addLast(new LoggingHandler(LogLevel.TRACE));
                }
                if (proxyHandler != null) {
                    ch.pipeline().addLast(proxyHandler.get());
                }
            }
        };
    }

    @Override
    public ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx) {
        return bootstrap.clone(ctx.channel().eventLoop()).connect(socketAddress);
    }

    @Override
    public ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx, Queue<Supplier<ProxyHandler>> upstreamProxyChain) {
        if (upstreamProxyChain == null || upstreamProxyChain.isEmpty()) {
            return channel(socketAddress, ctx);
        } else {
            EventLoop eventLoop = ctx.channel().eventLoop();
            Bootstrap bootstrap = bootstrap().clone(eventLoop);
            CompleteChannelPromise promise = new CompleteChannelPromise(eventLoop);
            channelPromise(socketAddress, upstreamProxyChain, bootstrap, promise);
            return promise;
        }
    }

    protected void channelPromise(InetSocketAddress socketAddress,
                                  Queue<Supplier<ProxyHandler>> proxiesChain,
                                  Bootstrap bootstrap,
                                  CompleteChannelPromise promise) {
        Supplier<ProxyHandler> proxyHandler = proxiesChain.poll();
        ChannelInitializer<Channel> channelInitializer = channelInitializer(proxyHandler);
        ChannelFuture channelFuture = bootstrap.handler(channelInitializer).connect(socketAddress);

        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                promise.setChannel(channelFuture.channel()).setSuccess();
            } else {
                Throwable cause = future.cause();
                logger.warn("Connection failed.", cause);
                if (proxiesChain.isEmpty()) {
                    promise.setFailure(cause);
                } else {
                    channelPromise(socketAddress, proxiesChain, bootstrap, promise);
                }
            }
        });
    }

    @Override
    public void switchUpstreamProxy(Supplier<ProxyHandler> upstreamProxy) {
        setUpstreamProxySupplier(upstreamProxy);
    }

    /*
    #####################################################################################
    ################################## Getter | Setter ##################################
    #####################################################################################
     */

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public DirectServerConnector setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
        return this;
    }

    public Supplier<ProxyHandler> getUpstreamProxySupplier() {
        return upstreamProxySupplier;
    }

    public DirectServerConnector setUpstreamProxySupplier(Supplier<ProxyHandler> upstreamProxySupplier) {
        this.upstreamProxySupplier = upstreamProxySupplier;
        return this;
    }
}
