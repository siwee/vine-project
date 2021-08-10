package io.github.aomsweet.petty.http;

import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
import io.github.aomsweet.petty.ResolveServerAddressException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class HttpTunnelDuplexClientRelayHandler extends HttpClientRelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelDuplexClientRelayHandler.class);

    /**
     * tls client hello request
     */
    Object clientHello;

    public HttpTunnelDuplexClientRelayHandler(PettyServer petty) {
        super(petty, logger);
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        ctx.pipeline().remove(HandlerNames.DECODER);
        try {
            serverAddress = resolveServerAddress(httpRequest);
        } catch (ResolveServerAddressException e) {
            e.printStackTrace();
        }
        ByteBuf byteBuf = ctx.alloc().buffer(TUNNEL_ESTABLISHED_RESPONSE.length);
        ctx.writeAndFlush(byteBuf.writeBytes(TUNNEL_ESTABLISHED_RESPONSE)).addListener(future -> {
            if (!future.isSuccess()) {
                ctx.close();
            }
        });
        doConnectServer(ctx, ctx.channel(), httpRequest);
    }

    @Override
    public void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) {
        ReferenceCountUtil.release(httpContent);
    }

    @Override
    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) {
        clientHello = message;
    }

    @Override
    protected void onConnected(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        addServerRelayHandler(ctx);
        if (clientHello != null) {
            relayChannel.writeAndFlush(clientHello);
        }
    }

    @Override
    protected void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        release(ctx);
    }

    @Override
    public void release(ChannelHandlerContext ctx) {
        super.release(ctx);
        if (clientHello != null) {
            ReferenceCountUtil.release(clientHello);
        }
    }
}
