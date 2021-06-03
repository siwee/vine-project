package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public abstract class ConnectHandler<Q> extends ChannelInboundHandlerAdapter {

    final InternalLogger logger;
    final CarawayServer caraway;
    ServerConnector connector;

    public ConnectHandler(CarawayServer caraway, InternalLogger logger) {
        this.logger = logger;
        this.caraway = caraway;
        this.connector = caraway.getConnector();
    }

    Future<Channel> doConnectServer(ChannelHandlerContext ctx, Channel clientChannel, Q request) {
        EventLoop eventLoop = clientChannel.eventLoop();
        Promise<Channel> promise = eventLoop.newPromise();
        promise.addListener((FutureListener<Channel>) future -> {
            final Channel serverChannel = future.getNow();
            if (future.isSuccess()) {
                if (clientChannel.isActive()) {
                    connected(ctx, clientChannel, serverChannel, request);
                } else {
                    ChannelUtils.closeOnFlush(serverChannel);
                }
            } else {
                this.failConnect(ctx, clientChannel, request);
            }
        });
        InetSocketAddress serverAddress = getServerAddress(request);
        connector.channel(serverAddress, eventLoop, promise).addListener(future -> {
            if (!future.isSuccess()) {
                this.failConnect(ctx, clientChannel, request);
            }
        });
        return promise;
    }

    abstract void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, Q request);

    abstract void failConnect(ChannelHandlerContext ctx, Channel clientChannel, Q request);

    abstract InetSocketAddress getServerAddress(Q request);

    public void relayDucking(Channel clientChannel, Channel serverChannel) {
        if (clientChannel.isActive()) {
            if (serverChannel.isActive()) {
                clientChannel.pipeline().addLast(new ClientRelayHandler(serverChannel));
                serverChannel.pipeline().addLast(new ServerRelayHandler(clientChannel));
            } else {
                ChannelUtils.closeOnFlush(clientChannel);
            }
        } else {
            ChannelUtils.closeOnFlush(serverChannel);
        }
    }

    public void release(Channel clientChannel, Channel serverChannel) {
        if (clientChannel != null) {
            ChannelUtils.closeOnFlush(clientChannel);
        }
        if (serverChannel != null) {
            ChannelUtils.closeOnFlush(serverChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
        logger.error(cause.getMessage(), cause);
    }
}
