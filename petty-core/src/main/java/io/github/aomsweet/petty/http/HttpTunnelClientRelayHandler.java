package io.github.aomsweet.petty.http;

import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
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
public class HttpTunnelClientRelayHandler extends HttpClientRelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelClientRelayHandler.class);

    /**
     * tls client hello request
     */
    Object clientHello;

    public HttpTunnelClientRelayHandler(PettyServer petty) {
        super(petty, logger);
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
        ctx.pipeline().remove(HandlerNames.DECODER);
        serverAddress = resolveServerAddress(httpRequest);
        ByteBuf byteBuf = ctx.alloc().buffer(TUNNEL_ESTABLISHED_RESPONSE.length);
        ctx.writeAndFlush(byteBuf.writeBytes(TUNNEL_ESTABLISHED_RESPONSE));
        doConnectServer(ctx, ctx.channel(), httpRequest);
    }

    @Override
    public void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) throws Exception {
        ReferenceCountUtil.release(httpContent);
    }

    @Override
    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) {
        clientHello = message;
    }

    @Override
    protected void onConnected(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        doServerRelay(ctx);
        if (clientHello != null) {
            relayChannel.writeAndFlush(clientHello);
            clientHello = null;
        }
    }

    @Override
    public void destroy(ChannelHandlerContext ctx) {
        if (clientHello != null) {
            ReferenceCountUtil.release(clientHello);
            clientHello = null;
        }
        super.destroy(ctx);
    }
}
