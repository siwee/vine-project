package io.github.aomsweet.petty.http.mitm;

import io.github.aomsweet.petty.ChannelUtils;
import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
import io.github.aomsweet.petty.ResolveServerAddressException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public class HttpMitmClientRelayHandler extends MitmClientRelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpMitmClientRelayHandler.class);

    public HttpMitmClientRelayHandler(PettyServer petty) {
        super(petty, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead0(ctx, msg);
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        InetSocketAddress serverAddress = resolveServerAddress(request);
        if (this.serverAddress == null) {
            this.serverAddress = serverAddress;
            httpMessages.offer(request);
            doConnectServer(ctx, ctx.channel(), request);
        } else if (this.serverAddress.equals(serverAddress)) {
            relay(ctx, request);
        } else {
            status = Status.UNCONNECTED;
            relayChannel.pipeline().remove(HandlerNames.RELAY);
            ChannelUtils.closeOnFlush(relayChannel);

            this.serverAddress = serverAddress;
            httpMessages.offer(request);
            doConnectServer(ctx, ctx.channel(), request);
        }
    }

    @Override
    public void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) {
        if (status == Status.CONNECTED) {
            relay(ctx, httpContent);
        } else {
            httpMessages.offer(httpContent);
        }
    }

    @Override
    public InetSocketAddress resolveServerAddress(HttpRequest httpRequest) throws ResolveServerAddressException {
        try {
            String uri = httpRequest.uri();
            String host;
            if (uri.charAt(0) == '/') {
                host = httpRequest.headers().get(HttpHeaderNames.HOST);
            } else {
                int index = uri.indexOf(':');
                char c = uri.charAt(index - 1);
                if (c == 's' || c == 'S') {
                    isSsl = true;
                }
                index = index + 3;
                int diag = uri.indexOf('/', index);
                host = diag == -1 ? uri.substring(index) : uri.substring(index, diag);
            }
            return resolveServerAddress(host, isSsl ? 443 : 80);
        } catch (Exception e) {
            throw new ResolveServerAddressException(getHttpRequestInitialLine(httpRequest), e);
        }
    }

    private InetSocketAddress resolveServerAddress(String host, int defaultPort) {
        int index = host.indexOf(':');
        if (index == -1) {
            return InetSocketAddress.createUnresolved(host, defaultPort);
        } else {
            return InetSocketAddress.createUnresolved(host.substring(0, index),
                Integer.parseInt(host.substring(index + 1)));
        }
    }
}
