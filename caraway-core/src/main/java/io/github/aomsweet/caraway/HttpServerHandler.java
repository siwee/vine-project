package io.github.aomsweet.caraway;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.logging.LoggingHandler;

/**
 * @author aomsweet
 */
public class HttpServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            if (httpRequest.decoderResult().isFailure()) {
                ctx.close();
            } else {
                Bootstrap bootstrap = new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler());
                if (HttpMethod.CONNECT.equals(httpRequest.method())) {
                    ctx.pipeline().addLast(new HttpTunnelConnectHandler(new DirectProxyConnector(bootstrap)));
                } else {
                    ctx.pipeline().addLast(new HttpConnectHandler(new DirectProxyConnector(bootstrap)));
                }
                ctx.fireChannelRead(msg).pipeline().remove(this);
            }
        }
    }
}
