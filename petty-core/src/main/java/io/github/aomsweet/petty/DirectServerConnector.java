package io.github.aomsweet.petty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;

/**
 * @author aomsweet
 */
public class DirectServerConnector implements ServerConnector {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(DirectServerConnector.class);

    Bootstrap bootstrap;

    public DirectServerConnector() {
        this.bootstrap = bootstrap();
    }

    public DirectServerConnector(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
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
        return channelInitializer(null);
    }

    public ChannelInitializer<Channel> channelInitializer(ProxyHandler proxyHandler) {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                if (logger.isTraceEnabled()) {
                    ch.pipeline().addLast(new LoggingHandler(LogLevel.TRACE));
                }
                if (proxyHandler != null) {
                    ch.pipeline().addLast(proxyHandler);
                }
            }
        };
    }

    @Override
    public ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx) {
        return bootstrap.clone(ctx.channel().eventLoop()).connect(socketAddress);
    }

    @Override
    public ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx, List<ProxyInfo> upstreamProxies) {
        if (upstreamProxies == null || upstreamProxies.isEmpty()) {
            return channel(socketAddress, ctx);
        } else {
            EventLoop eventLoop = ctx.channel().eventLoop();
            Bootstrap bootstrap = bootstrap().clone(eventLoop);

            if (upstreamProxies.size() == 1) {
                ProxyInfo proxyInfo = upstreamProxies.get(0);
                ProxyHandler proxyHandler = proxyInfo.newProxyHandler();
                ChannelInitializer<Channel> initHandler = channelInitializer(proxyHandler);
                return bootstrap.handler(initHandler).connect(socketAddress);
            } else {
                CompleteChannelPromise promise = new CompleteChannelPromise(eventLoop);
                channelPromise(socketAddress, upstreamProxies.iterator(), bootstrap, promise);
                return promise;
            }
        }
    }

    protected void channelPromise(InetSocketAddress socketAddress,
                                  Iterator<ProxyInfo> upstreamProxies,
                                  Bootstrap bootstrap,
                                  CompleteChannelPromise promise) {
        ProxyInfo proxyInfo = upstreamProxies.next();
        ProxyHandler proxyHandler = proxyInfo.newProxyHandler();
        ChannelInitializer<Channel> initHandler = channelInitializer(proxyHandler);
        bootstrap.handler(initHandler).connect(socketAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                promise.setChannel(future.channel()).setSuccess();
            } else {
                Throwable cause = future.cause();
                logger.warn("Connection failed.", cause);
                if (upstreamProxies.hasNext()) {
                    channelPromise(socketAddress, upstreamProxies, bootstrap, promise);
                } else {
                    promise.setFailure(cause);
                }
            }
        });
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

}
