package io.github.aomsweet.caraway.http.mitm;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.ResolveServerAddressException;
import io.github.aomsweet.caraway.http.HttpConnectHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author aomsweet
 */
public abstract class MitmConnectHandler extends HttpConnectHandler {

    Channel clientChannel;
    Channel serverChannel;

    InetSocketAddress serverAddress;
    Queue<Object> queue;

    public MitmConnectHandler(CarawayServer caraway, InternalLogger logger) {
        super(caraway, logger);
        this.queue = new ArrayDeque<>(2);
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        try {
            this.clientChannel = ctx.channel();
            handleHttpRequest0(ctx, httpRequest);
        } catch (Exception e) {
            release(clientChannel, serverChannel);
        }
    }

    public abstract void handleHttpRequest0(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception;

    @Override
    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) {
        flush();
        ctx.close();
        ReferenceCountUtil.release(message);
    }

    @Override
    protected void failConnect(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        release(clientChannel, null);
    }

    public void flush() {
        flush(null);
    }

    public void flush(ChannelHandlerContext ctx) {
        Object message;
        while ((message = queue.poll()) != null) {
            if (ctx == null) {
                ReferenceCountUtil.release(message);
            } else {
                ctx.fireChannelRead(message);
            }
        }
    }

    @Override
    public void release(Channel clientChannel, Channel serverChannel) {
        flush(null);
        super.release(clientChannel, serverChannel);
    }

    @Override
    protected InetSocketAddress getServerAddress(HttpRequest request) throws ResolveServerAddressException {
        return serverAddress;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        release(clientChannel, serverChannel);
        logger.error(cause.getMessage(), cause);
    }
}
