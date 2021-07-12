package io.github.aomsweet.caraway.http.mitm;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.ChannelUtils;
import io.github.aomsweet.caraway.http.HttpTunnelConnectHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import java.util.ArrayDeque;

/**
 * @author aomsweet
 */
public class HttpsMitmConnectHandler extends MitmConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpsMitmConnectHandler.class);

    boolean connected;
    boolean sslHandshakeCompleted;

    public HttpsMitmConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
        this.queue = new ArrayDeque<>(2);
    }

    @Override
    public void handleHttpRequest0(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        if (HttpMethod.CONNECT.equals(request.method())) {
            this.serverAddress = resolveServerAddress(request);
            byte[] bytes = HttpTunnelConnectHandler.TUNNEL_ESTABLISHED_RESPONSE;
            ByteBuf byteBuf = ctx.alloc().buffer(bytes.length);
            ctx.writeAndFlush(byteBuf.writeBytes(bytes));

            String host = serverAddress.getHostName();
            MitmManager mitmManager = caraway.getMitmManager();
            SslContext sslContext = mitmManager.serverSslContext(host);
            ctx.pipeline().addFirst(sslContext.newHandler(ctx.alloc()));

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
    protected void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        connected = true;
        this.clientChannel = clientChannel;
        this.serverChannel = serverChannel;

        try {
            ChannelPipeline pipeline = serverChannel.pipeline();
            SslContext clientSslContext = caraway.getClientSslContext();
            pipeline.addLast(clientSslContext.newHandler(serverChannel.alloc()));
            pipeline.addLast(new HttpRequestEncoder());
            tryDucking(ctx);
        } catch (SSLException e) {
            logger.error(e.getMessage(), e);
            release(clientChannel, serverChannel);
        }
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

    @Override
    public void flush(ChannelHandlerContext ctx) {
        Object message;
        while ((message = queue.poll()) != null) {
            if (ctx == null) {
                ReferenceCountUtil.release(message);
            } else {
                ctx.pipeline().fireChannelRead(message);
            }
        }
    }
}
