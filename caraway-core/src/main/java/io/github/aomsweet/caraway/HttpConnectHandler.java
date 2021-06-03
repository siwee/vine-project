package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
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
public class HttpConnectHandler extends ConnectHandler<HttpRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpConnectHandler.class);

    public static SslContext clientSslContext;

    boolean isSsl;
    boolean connected;
    final Queue<Object> queue;
    InetSocketAddress serverAddress;

    Channel clientChannel;
    Channel serverChannel;

    public HttpConnectHandler(CarawayServer caraway) {
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
            clearQueue(null);
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
                serverPipeline.addLast(getClientSslContext().newHandler(serverChannel.alloc()));
            }
            serverPipeline.addLast(new HttpRequestEncoder());
            serverChannel.writeAndFlush(request).addListener(future -> {
                if (future.isSuccess()) {
                    connected = true;
                    relayDucking(clientChannel, serverChannel);
                    clearQueue(ctx);
                    clientChannel.config().setAutoRead(true);
                } else {
                    release(clientChannel, serverChannel);
                    logger.error("{} Failed to request server: {}",
                        serverChannel, serverAddress, future.cause());
                }
            });
        } catch (SSLException e) {
            release(clientChannel, serverChannel);
        }
    }

    @Override
    void failConnect(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        ChannelUtils.closeOnFlush(clientChannel);
        clearQueue(null);
    }

    @Override
    InetSocketAddress getServerAddress(HttpRequest request) {
        return serverAddress;
    }

    @Override
    public void release(Channel clientChannel, Channel serverChannel) {
        clearQueue(null);
        super.release(clientChannel, serverChannel);
    }

    public void clearQueue(ChannelHandlerContext ctx) {
        Object httpContent;
        while ((httpContent = queue.poll()) != null) {
            if (ctx == null) {
                ReferenceCountUtil.release(httpContent);
            } else {
                ctx.fireChannelRead(httpContent);
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

    public static SslContext getClientSslContext() throws SSLException {
        if (clientSslContext == null) {
            synchronized (HttpConnectHandler.class) {
                if (clientSslContext == null) {
                    //https://github.com/GlowstoneMC/Glowstone/blob/5b89f945b4/src/main/java/net/glowstone/net/http/HttpClient.java
                    clientSslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                }
            }
        }
        return clientSslContext;
    }
}
