package io.github.aomsweet.caraway.http;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.http.mitm.HttpMitmConnectHandler;
import io.github.aomsweet.caraway.http.mitm.HttpsMitmConnectHandler;
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
                if (caraway.getMitmManager() == null) {
                    ctx.pipeline().addLast(new HttpTunnelDuplexConnectHandler(caraway));
                } else {
                    ctx.pipeline().addLast(new HttpsMitmConnectHandler(caraway));
                }
            } else {
                ctx.pipeline().addLast(new HttpMitmConnectHandler(caraway));
            }
            ctx.fireChannelRead(httpRequest).pipeline().remove(this);
        }
    }
}
