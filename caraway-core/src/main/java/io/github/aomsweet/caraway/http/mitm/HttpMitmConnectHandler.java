package io.github.aomsweet.caraway.http.mitm;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.ChannelUtils;
import io.github.aomsweet.caraway.RelayHandler;
import io.github.aomsweet.caraway.ResolveServerAddressException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;

/**
 * @author aomsweet
 */
public class HttpMitmConnectHandler extends MitmConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpMitmConnectHandler.class);

    boolean isSsl;
    boolean connected;

    public HttpMitmConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
        this.queue = new ArrayDeque<>(4);
    }

    @Override
    public void handleHttpRequest0(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        InetSocketAddress serverAddress = resolveServerAddress(request);
        if (this.serverAddress == null) {
            this.serverAddress = serverAddress;
            doConnectServer(ctx, ctx.channel(), request);
        } else if (this.serverAddress.equals(serverAddress)) {
            ctx.fireChannelRead(request);
        } else {
            this.serverAddress = serverAddress;
            clientChannel.pipeline().remove(RelayHandler.class);
            serverChannel.pipeline().remove(RelayHandler.class);
            ChannelUtils.closeOnFlush(serverChannel);
            doConnectServer(ctx, ctx.channel(), request);
        }
    }

    @Override
    public void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) {
        if (connected) {
            ctx.fireChannelRead(httpContent);
        } else {
            queue.offer(httpContent);
        }
    }

    @Override
    protected Future<Channel> doConnectServer(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        this.connected = false;
        ctx.channel().config().setAutoRead(false);
        return super.doConnectServer(ctx, ctx.channel(), request);
    }

    @Override
    protected void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        this.clientChannel = clientChannel;
        this.serverChannel = serverChannel;
        try {
            ChannelPipeline serverPipeline = serverChannel.pipeline();
            if (isSsl) {
                SslContext clientSslContext = caraway.getClientSslContext();
                serverPipeline.addLast(clientSslContext.newHandler(serverChannel.alloc(),
                    serverAddress.getHostName(), serverAddress.getPort()));
            }
            serverPipeline.addLast(new HttpRequestEncoder());
            if (relayDucking(clientChannel, serverChannel)) {
                ctx.fireChannelRead(request);
                flush(ctx);
                connected = true;
                clientChannel.config().setAutoRead(true);
            } else {
                release(clientChannel, serverChannel);
            }
        } catch (SSLException e) {
            release(clientChannel, serverChannel);
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
