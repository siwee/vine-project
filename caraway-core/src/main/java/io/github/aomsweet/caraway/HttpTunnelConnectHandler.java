package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
@ChannelHandler.Sharable
public class HttpTunnelConnectHandler extends ConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelConnectHandler.class);

    public static final byte[] ESTABLISHED_BYTES = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes();

    public HttpTunnelConnectHandler(CarawayServer caraway) {
        super(caraway);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            InetSocketAddress remoteAddress = getRemoteAddress((HttpRequest) msg);
            final Channel inboundChannel = ctx.channel();
            final ChannelPipeline inboundPipeline = inboundChannel.pipeline();
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener((GenericFutureListener<Future<Channel>>) connect -> {
                if (connect.isSuccess()) {
                    Channel outboundChannel = connect.getNow();
                    ChannelPipeline outboundPipeline = outboundChannel.pipeline();
                    inboundPipeline.remove(this);
                    inboundPipeline.remove(HttpRequestDecoder.class);
                    ctx.writeAndFlush(ctx.alloc().buffer(ESTABLISHED_BYTES.length)
                        .writeBytes(ESTABLISHED_BYTES)).addListener(future -> {
                        if (future.isSuccess()) {
                            inboundPipeline.addLast(new RelayHandler(outboundChannel));
                            outboundPipeline.addLast(new RelayHandler(inboundChannel));
                        } else {
                            ctx.close();
                            outboundPipeline.close();
                        }
                    });
                } else {
                    ctx.close();
                }
            });
            connector.channel(remoteAddress, inboundChannel.eventLoop(), promise);
        } else if (msg instanceof HttpContent) {
            ReferenceCountUtil.release(msg);
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    public InetSocketAddress getRemoteAddress(HttpRequest httpRequest) {
        String uri = httpRequest.uri();
        int index = uri.indexOf(':');
        if (index > -1) {
            return InetSocketAddress.createUnresolved(uri.substring(0, index), Integer.parseInt(uri.substring(index + 1)));
        } else {
            String host = httpRequest.headers().get(HttpHeaderNames.HOST);
            if (host == null) {
                throw new RuntimeException("Bad request: " + httpRequest.method() + ' ' + uri + ' ' + httpRequest.protocolVersion());
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
