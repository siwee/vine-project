package io.github.aomsweet.caraway.http;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.ConnectHandler;
import io.github.aomsweet.caraway.ResolveServerAddressException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public abstract class HttpConnectHandler extends ConnectHandler<HttpRequest> {

    public static final byte[] TUNNEL_ESTABLISHED_RESPONSE = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes();

    public HttpConnectHandler(CarawayServer caraway, InternalLogger logger) {
        super(caraway, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            if (httpRequest.decoderResult().isSuccess()) {
                handleHttpRequest(ctx, httpRequest);
            } else {
                ctx.close();
            }
        } else if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            if (httpContent.decoderResult().isSuccess()) {
                handleHttpContent(ctx, httpContent);
            } else {
                ctx.close();
            }
        } else {
            handleUnknownMessage(ctx, msg);
        }
    }

    public abstract void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest);

    public abstract void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent);

    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) {
        ctx.fireChannelRead(message);
    }

    public InetSocketAddress resolveServerAddress(HttpRequest httpRequest) throws ResolveServerAddressException {
        try {
            String uri = httpRequest.uri();
            int index = uri.indexOf(':');
            return InetSocketAddress.createUnresolved(uri.substring(0, index), Integer.parseInt(uri.substring(index + 1)));
        } catch (Exception e) {
            throw new ResolveServerAddressException(getHttpRequestInitialLine(httpRequest), e);
        }
    }

    public String getHttpRequestInitialLine(HttpRequest httpRequest) {
        return httpRequest.method().name() + ' ' + httpRequest.uri() + ' ' + httpRequest.protocolVersion();
    }

}
