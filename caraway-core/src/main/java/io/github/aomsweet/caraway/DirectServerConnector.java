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
        return new Bootstrap()
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(channelInitializer());
    }

    public ChannelInitializer<Channel> channelInitializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                if (logger.isTraceEnabled()) {
                    ch.pipeline().addLast(new LoggingHandler(LogLevel.TRACE));
                }
                if (upstreamProxySupplier != null) {
                    ch.pipeline().addLast(upstreamProxySupplier.get());
                }
            }
        };
    }

    @Override
    public ChannelFuture channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup) {
        return bootstrap.clone(eventLoopGroup).connect(socketAddress);
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

    @Override
    public void switchUpstreamProxy(Supplier<ProxyHandler> upstreamProxySupplier) {
        setUpstreamProxySupplier(upstreamProxySupplier);
    }
}
