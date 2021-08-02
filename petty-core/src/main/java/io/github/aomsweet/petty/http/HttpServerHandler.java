package io.github.aomsweet.petty.http;

import io.github.aomsweet.petty.PettyServer;
import io.github.aomsweet.petty.http.mitm.HttpMitmConnectHandler;
import io.github.aomsweet.petty.http.mitm.HttpsMitmConnectHandler;
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

    PettyServer petty;

    public HttpServerHandler(PettyServer petty) {
        this.petty = petty;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
        if (httpRequest.decoderResult().isFailure()) {
            ctx.close();
        } else {
            if (HttpMethod.CONNECT.equals(httpRequest.method())) {
                if (petty.getMitmManager() == null) {
                    ctx.pipeline().addLast(new HttpTunnelDuplexConnectHandler(petty));
                } else {
                    ctx.pipeline().addLast(new HttpsMitmConnectHandler(petty));
                }
            } else {
                ctx.pipeline().addLast(new HttpMitmConnectHandler(petty));
            }
            ctx.fireChannelRead(httpRequest).pipeline().remove(this);
        }
    }
}
