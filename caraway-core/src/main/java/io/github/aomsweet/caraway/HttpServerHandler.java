package io.github.aomsweet.caraway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @author aomsweet
 */
@ChannelHandler.Sharable
public class HttpServerHandler extends SimpleChannelInboundHandler<HttpRequest> {

    public static final byte[] UNAUTHORIZED_BYTES = "HTTP/1.1 407 Unauthorized\r\nProxy-Authenticate: Basic realm=\"Access to the staging site\"\r\n\r\n".getBytes();

    CarawayServer caraway;

    public HttpServerHandler(CarawayServer caraway) {
        this.caraway = caraway;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
        if (httpRequest.decoderResult().isFailure()) {
            ctx.close();
        } else {
            ProxyAuthenticator authenticator = caraway.getProxyAuthenticator();
            if (!(authenticator == null || authenticator.authenticate(httpRequest))) {
                ByteBuf byteBuf = ctx.alloc().buffer(UNAUTHORIZED_BYTES.length);
                ctx.writeAndFlush(byteBuf.writeBytes(UNAUTHORIZED_BYTES)).addListener(ChannelFutureListener.CLOSE);
                return;
            }

            if (HttpMethod.CONNECT.equals(httpRequest.method())) {
                if (caraway.getMitmManager() == null) {
                    ctx.pipeline().addLast(new HttpTunnelConnectHandler(caraway));
                } else {
                    ctx.pipeline().addLast(new HttpsMitmConnectHandler(caraway));
                }
            } else {
                ctx.pipeline().addLast(new HttpConnectHandler(caraway));
            }
            ctx.fireChannelRead(httpRequest).pipeline().remove(this);
        }
    }
}
