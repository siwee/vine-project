package io.github.aomsweet.caraway;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public class DirectServerConnector implements ServerConnector {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(DirectServerConnector.class);

    Bootstrap bootstrap;

    public DirectServerConnector() {
        this.bootstrap = new Bootstrap()
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    if (logger.isTraceEnabled()) {
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.TRACE));
                    }
                    ch.pipeline().addLast(new HttpProxyHandler(new InetSocketAddress("localhost", 7891)));
                }
            });
    }

    public DirectServerConnector(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public ChannelFuture channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup) {
        return bootstrap.clone(eventLoopGroup).connect(socketAddress);
    }
}
