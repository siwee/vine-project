package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author aomsweet
 */
public class HttpMitmConnectHandler extends ConnectHandler<HttpRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpMitmConnectHandler.class);

    boolean isSsl;
    boolean connected;
    final Queue<Object> queue;
    InetSocketAddress serverAddress;

    Channel clientChannel;
    Channel serverChannel;

    public HttpMitmConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
        this.queue = new ArrayDeque<>(4);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if (logger.isDebugEnabled()) {
                logger.debug(ctx.channel() + " Accept request: {}", request);
            }
            InetSocketAddress serverAddress = getServerAddress0(request);
            if (this.serverAddress == null) {
                this.serverAddress = serverAddress;
                doConnectServer(ctx, ctx.channel(), request);
            } else if (this.serverAddress.equals(serverAddress)) {
                ctx.fireChannelRead(request);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug(ctx.channel() + " Switch the remote address [{}] to [{}]",
                        this.serverAddress, serverAddress);
                }
                this.serverAddress = serverAddress;
                clientChannel.pipeline().remove(RelayHandler.class);
                serverChannel.pipeline().remove(RelayHandler.class);
                ChannelUtils.closeOnFlush(serverChannel);
                doConnectServer(ctx, ctx.channel(), request);
            }
        } else if (msg instanceof HttpContent) {
            if (connected) {
                ctx.fireChannelRead(msg);
            } else {
                queue.offer(msg);
            }
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
            flush();
        }
    }

    @Override
    Future<Channel> doConnectServer(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        this.connected = false;
        ctx.channel().config().setAutoRead(false);
        return super.doConnectServer(ctx, ctx.channel(), request);
    }

    @Override
    void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        this.clientChannel = clientChannel;
        this.serverChannel = serverChannel;
        try {
            ChannelPipeline serverPipeline = serverChannel.pipeline();
            if (isSsl) {
                SslContext clientSslContext = caraway.getClientSslContext();
                serverPipeline.addLast(clientSslContext.newHandler(serverChannel.alloc()));
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
    void failConnect(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        ChannelUtils.closeOnFlush(clientChannel);
        flush();
    }

    @Override
    InetSocketAddress getServerAddress(HttpRequest request) {
        return serverAddress;
    }

    @Override
    public void release(Channel clientChannel, Channel serverChannel) {
        flush();
        super.release(clientChannel, serverChannel);
    }

    public void flush() {
        flush(null);
    }

    public void flush(ChannelHandlerContext ctx) {
        Object message;
        while ((message = queue.poll()) != null) {
            if (ctx == null) {
                ReferenceCountUtil.release(message);
            } else {
                ctx.fireChannelRead(message);
            }
        }
    }

    public InetSocketAddress getServerAddress0(HttpRequest httpRequest) {
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
        if (host == null) {
            throw new RuntimeException("Bad request: " + httpRequest.method() + ' ' + uri + ' ' + httpRequest.protocolVersion());
        } else {
            return getServerAddress0(host, isSsl ? 443 : 80);
        }
    }

    private InetSocketAddress getServerAddress0(String host, int defaultPort) {
        int index = host.indexOf(':');
        if (index == -1) {
            return InetSocketAddress.createUnresolved(host, defaultPort);
        } else {
            return InetSocketAddress.createUnresolved(host.substring(0, index),
                Integer.parseInt(host.substring(index + 1)));
        }
    }
}
