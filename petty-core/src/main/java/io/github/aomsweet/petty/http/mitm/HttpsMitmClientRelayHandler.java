package io.github.aomsweet.petty.http.mitm;

import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class HttpsMitmClientRelayHandler extends MitmClientRelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpsMitmClientRelayHandler.class);

    boolean sslHandshakeCompleted;

    public HttpsMitmClientRelayHandler(PettyServer petty) {
        super(petty, logger);
        this.isSsl = true;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (state == State.READY && petty.getHttpInterceptorManager() == null) {
            relay(ctx, msg);
        } else {
            channelRead0(ctx, msg);
        }
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        if (HttpMethod.CONNECT.equals(request.method())) {
            this.serverAddress = resolveServerAddress(request);
            ByteBuf byteBuf = ctx.alloc().buffer(TUNNEL_ESTABLISHED_RESPONSE.length);
            ctx.writeAndFlush(byteBuf.writeBytes(TUNNEL_ESTABLISHED_RESPONSE));

            String host = serverAddress.getHostName();
            MitmManager mitmManager = petty.getMitmManager();
            SslContext sslContext = mitmManager.serverSslContext(host);
            ctx.pipeline().addFirst(HandlerNames.SSL, sslContext.newHandler(ctx.alloc()));

            doConnectServer(ctx, ctx.channel(), request);
        } else if (state == State.READY) {
            relay(ctx, request);
        } else {
            httpMessages.offer(request);
        }
    }

    @Override
    public void doServerRelay(ChannelHandlerContext ctx) {
        if (sslHandshakeCompleted) {
            super.doServerRelay(ctx);
        }
    }

    @Override
    public void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) {
        if (sslHandshakeCompleted) {
            httpMessages.offer(httpContent);
        } else if (state == State.READY) {
            relay(ctx, httpContent);
        } else {
            ReferenceCountUtil.release(httpContent);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            if (((SslHandshakeCompletionEvent) evt).isSuccess()) {
                sslHandshakeCompleted = true;
                if (state == State.CONNECTED) {
                    super.doServerRelay(ctx);
                }
            }
        }
    }
}
