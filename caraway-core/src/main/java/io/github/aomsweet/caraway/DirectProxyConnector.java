package io.github.aomsweet.caraway;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public class DirectProxyConnector extends ProxyConnector {

    public DirectProxyConnector(Bootstrap bootstrap) {
        super(bootstrap);
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
