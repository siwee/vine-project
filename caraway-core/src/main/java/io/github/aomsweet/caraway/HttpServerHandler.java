package io.github.aomsweet.caraway;

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

    CarawayServer caraway;

    public HttpServerHandler(CarawayServer caraway) {
        this.caraway = caraway;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
        if (httpRequest.decoderResult().isFailure()) {
            ctx.close();
        } else {
            if (HttpMethod.CONNECT.equals(httpRequest.method())) {
                ctx.pipeline().addLast(new HttpTunnelConnectHandler(caraway));
            } else {
                ctx.pipeline().addLast(new HttpConnectHandler(caraway));
            }
            ctx.fireChannelRead(httpRequest).pipeline().remove(this);
        }
    }
}
