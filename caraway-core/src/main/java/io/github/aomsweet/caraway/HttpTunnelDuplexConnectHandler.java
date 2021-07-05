package io.github.aomsweet.caraway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class HttpTunnelDuplexConnectHandler extends HttpTunnelConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelDuplexConnectHandler.class);

    /**
     * tls client hello request
     */
    Object clientHello;

    public HttpTunnelDuplexConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            ctx.pipeline().remove(HttpRequestDecoder.class);
            ByteBuf byteBuf = ctx.alloc().buffer(ESTABLISHED_BYTES.length);
            ctx.writeAndFlush(byteBuf.writeBytes(ESTABLISHED_BYTES)).addListener(future -> {
                if (!future.isSuccess()) {
                    ctx.close();
                }
            });
            doConnectServer(ctx, ctx.channel(), (HttpRequest) msg);
        } else if (msg instanceof HttpContent) {
            ReferenceCountUtil.release(msg);
        } else {
            clientHello = msg;
        }
    }

    @Override
    void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        ChannelPipeline clientPipeline = clientChannel.pipeline().remove(this);
        if (relayDucking(clientChannel, serverChannel)) {
            if (clientHello != null) {
                clientPipeline.fireChannelRead(clientHello);
            }
        }
    }

    @Override
    public void release(Channel clientChannel, Channel serverChannel) {
        super.release(clientChannel, serverChannel);
        if (clientHello != null) {
            ReferenceCountUtil.release(clientHello);
        }
    }
}
