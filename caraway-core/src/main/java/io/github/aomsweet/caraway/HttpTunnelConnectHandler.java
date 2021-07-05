package io.github.aomsweet.caraway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
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
public class HttpTunnelConnectHandler extends ConnectHandler<HttpRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelConnectHandler.class);

    public static final byte[] ESTABLISHED_BYTES = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes();

    public HttpTunnelConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
    }

    public HttpTunnelConnectHandler(CarawayServer caraway, InternalLogger logger) {
        super(caraway, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            doConnectServer(ctx, ctx.channel(), (HttpRequest) msg);
        } else if (msg instanceof HttpContent) {
            ReferenceCountUtil.release(msg);
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    @Override
    void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        ChannelPipeline clientPipeline = clientChannel.pipeline();
        clientPipeline.remove(this);
        clientPipeline.remove(HttpRequestDecoder.class);
        ByteBuf byteBuf = ctx.alloc().buffer(ESTABLISHED_BYTES.length);
        ctx.writeAndFlush(byteBuf.writeBytes(ESTABLISHED_BYTES)).addListener(future -> {
            if (future.isSuccess()) {
                relayDucking(clientChannel, serverChannel);
            } else {
                release(clientChannel, serverChannel);
            }
        });
    }

    @Override
    void failConnect(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        ChannelUtils.closeOnFlush(clientChannel);
    }

    @Override
    InetSocketAddress getServerAddress(HttpRequest request) {
        String uri = request.uri();
        int index = uri.indexOf(':');
        if (index > -1) {
            return InetSocketAddress.createUnresolved(uri.substring(0, index), Integer.parseInt(uri.substring(index + 1)));
        } else {
            String host = request.headers().get(HttpHeaderNames.HOST);
            if (host == null) {
                throw new RuntimeException("Bad request: " + request.method() + ' ' + uri + ' ' + request.protocolVersion());
            } else {
                index = host.indexOf(':');
                if (index > -1) {
                    return InetSocketAddress.createUnresolved(uri.substring(0, index), Integer.parseInt(uri.substring(index + 1)));
                } else {
                    return InetSocketAddress.createUnresolved(host, 443);
                }
            }
        }
    }
}
