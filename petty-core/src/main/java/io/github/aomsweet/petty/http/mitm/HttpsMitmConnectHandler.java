package io.github.aomsweet.petty.http.mitm;

import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
import io.github.aomsweet.petty.ChannelUtils;
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
public class HttpsMitmConnectHandler extends MitmConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpsMitmConnectHandler.class);

    boolean sslHandshakeCompleted;

    public HttpsMitmConnectHandler(PettyServer petty) {
        super(petty, logger);
        this.isSsl = true;
    }

    @Override
    public void handleHttpRequest0(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        if (HttpMethod.CONNECT.equals(request.method())) {
            this.serverAddress = resolveServerAddress(request);
            ByteBuf byteBuf = ctx.alloc().buffer(TUNNEL_ESTABLISHED_RESPONSE.length);
            ctx.writeAndFlush(byteBuf.writeBytes(TUNNEL_ESTABLISHED_RESPONSE));

            String host = serverAddress.getHostName();
            MitmManager mitmManager = petty.getMitmManager();
            SslContext sslContext = mitmManager.serverSslContext(host);
            ctx.pipeline().addFirst(HandlerNames.SSL, sslContext.newHandler(ctx.alloc()));

            doConnectServer(ctx, ctx.channel(), request);
        } else {
            queue.offer(request);
        }
    }

    @Override
    public void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) {
        if (sslHandshakeCompleted) {
            queue.offer(httpContent);
        } else {
            ReferenceCountUtil.release(httpContent);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            if (((SslHandshakeCompletionEvent) evt).isSuccess()) {
                sslHandshakeCompleted = true;
                tryDucking(ctx);
            } else {
                ctx.close();
                if (serverChannel != null) {
                    ChannelUtils.closeOnFlush(serverChannel);
                }
            }
        }
    }

    @Override
    protected void doRelayDucking(ChannelHandlerContext ctx, HttpRequest request) {
        tryDucking(ctx);
    }

    void tryDucking(ChannelHandlerContext ctx) {
        if (connected && sslHandshakeCompleted) {
            ctx.pipeline().remove(this);
            if (relayDucking(clientChannel, serverChannel)) {
                flush(ctx);
            } else {
                release(clientChannel, serverChannel);
            }
        }
    }
}
