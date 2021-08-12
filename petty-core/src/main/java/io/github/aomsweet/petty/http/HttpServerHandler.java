package io.github.aomsweet.petty.http;

import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
import io.github.aomsweet.petty.http.mitm.HttpMitmClientRelayHandler;
import io.github.aomsweet.petty.http.mitm.HttpsMitmClientRelayHandler;
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
                    ctx.pipeline().addLast(HandlerNames.RELAY, new HttpTunnelDuplexClientRelayHandler(petty));
                } else {
                    ctx.pipeline().addLast(HandlerNames.RELAY, new HttpsMitmClientRelayHandler(petty));
                }
            } else {
                ctx.pipeline().addLast(HandlerNames.RELAY, new HttpMitmClientRelayHandler(petty));
            }
            ctx.fireChannelRead(httpRequest).pipeline().remove(this);
        }
    }
}
