package io.github.aomsweet.caraway.http;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.ChannelUtils;
import io.github.aomsweet.caraway.ResolveServerAddressException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
@ChannelHandler.Sharable
public class HttpTunnelConnectHandler extends HttpConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelConnectHandler.class);

    public HttpTunnelConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
    }

    public HttpTunnelConnectHandler(CarawayServer caraway, InternalLogger logger) {
        super(caraway, logger);
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        if(getChainedProxyManager() != null){

        }
        doConnectServer(ctx, ctx.channel(), httpRequest);
    }

    @Override
    public void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) {
        ReferenceCountUtil.release(httpContent);
    }

    @Override
    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) {
        ReferenceCountUtil.release(message);
        ctx.close();
    }

    @Override
    protected void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        ChannelPipeline clientPipeline = clientChannel.pipeline();
        clientPipeline.remove(this);
        clientPipeline.remove(HttpRequestDecoder.class);
        ByteBuf byteBuf = ctx.alloc().buffer(TUNNEL_ESTABLISHED_RESPONSE.length);
        ctx.writeAndFlush(byteBuf.writeBytes(TUNNEL_ESTABLISHED_RESPONSE)).addListener(future -> {
            if (future.isSuccess()) {
                relayDucking(clientChannel, serverChannel);
            } else {
                release(clientChannel, serverChannel);
            }
        });
    }

    @Override
    protected void failConnect(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        ChannelUtils.closeOnFlush(clientChannel);
    }

    @Override
    protected InetSocketAddress getServerAddress(HttpRequest request) throws ResolveServerAddressException {
        return resolveServerAddress(request);
    }
}
