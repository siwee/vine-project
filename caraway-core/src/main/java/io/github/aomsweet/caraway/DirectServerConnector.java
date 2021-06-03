package io.github.aomsweet.caraway;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
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
                    if (logger.isDebugEnabled()) {
                        ch.pipeline().addLast(new LoggingHandler());
                    }
                }
            });
    }

    public DirectServerConnector(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public Future<Channel> channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup) {
        return channel(socketAddress, eventLoopGroup, eventLoopGroup.next().newPromise());
    }

    @Override
    public Future<Channel> channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup, Promise<Channel> promise) {
        bootstrap.clone(eventLoopGroup).handler(new ChannelInboundHandlerAdapter() {

            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.pipeline().remove(this);
                promise.setSuccess(ctx.channel());
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                promise.setFailure(cause);
            }

        }).connect(socketAddress);
        return promise;
    }
}
