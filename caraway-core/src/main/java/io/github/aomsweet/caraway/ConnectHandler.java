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

    protected final InternalLogger logger;
    protected final CarawayServer caraway;

    public ConnectHandler(CarawayServer caraway, InternalLogger logger) {
        this.logger = logger;
        this.caraway = caraway;
    }

    protected Future<Channel> doConnectServer(ChannelHandlerContext ctx, Channel clientChannel, Q request) {
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
        try {
            InetSocketAddress serverAddress = getServerAddress(request);
            ServerConnector connector = caraway.getConnector();
            connector.channel(serverAddress, eventLoop, promise).addListener(future -> {
                if (!future.isSuccess()) {
                    this.failConnect(ctx, clientChannel, request);
                }
            });
        } catch (ResolveServerAddressException e) {
            promise.tryFailure(e);
        }
        return promise;
    }

    protected abstract void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, Q request);

    protected abstract void failConnect(ChannelHandlerContext ctx, Channel clientChannel, Q request);

    protected abstract InetSocketAddress getServerAddress(Q request) throws ResolveServerAddressException;

    public boolean relayDucking(Channel clientChannel, Channel serverChannel) {
        if (clientChannel.isActive()) {
            if (serverChannel.isActive()) {
                clientChannel.pipeline().addLast(new ClientRelayHandler(serverChannel));
                serverChannel.pipeline().addLast(new ServerRelayHandler(clientChannel));

                System.err.println("clientChannel channel: " + clientChannel.pipeline());
                System.err.println("serverChannel channel: " + serverChannel.pipeline());
                return true;
            } else {
                ChannelUtils.closeOnFlush(clientChannel);
            }
        } else {
            ChannelUtils.closeOnFlush(serverChannel);
        }
        return false;
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
