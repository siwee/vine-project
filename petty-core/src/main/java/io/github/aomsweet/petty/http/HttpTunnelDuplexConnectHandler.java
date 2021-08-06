package io.github.aomsweet.petty.http;

import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
import io.github.aomsweet.petty.ResolveServerAddressException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public class HttpTunnelDuplexConnectHandler extends HttpConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelDuplexConnectHandler.class);

    /**
     * tls client hello request
     */
    Object clientHello;

    public HttpTunnelDuplexConnectHandler(PettyServer petty) {
        super(petty, logger);
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        ctx.pipeline().remove(HandlerNames.DECODER);
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
    protected void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        ChannelPipeline clientPipeline = clientChannel.pipeline().remove(this);
        if (relayDucking(clientChannel, serverChannel)) {
            if (clientHello != null) {
                clientPipeline.fireChannelRead(clientHello);
            }
        }
    }

    @Override
    protected void failConnect(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        release(clientChannel, null);
    }

    @Override
    protected InetSocketAddress getServerAddress(HttpRequest request) throws ResolveServerAddressException {
        return resolveServerAddress(request);
    }

    @Override
    public void release(Channel clientChannel, Channel serverChannel) {
        super.release(clientChannel, serverChannel);
        if (clientHello != null) {
            ReferenceCountUtil.release(clientHello);
        }
    }
}
