package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author aomsweet
 */
public class HttpConnectHandler extends ConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpConnectHandler.class);

    public static SslContext clientSslContext;

    boolean isSsl;
    boolean connected;
    final Queue<Object> queue;
    InetSocketAddress remoteAddress;

    Channel inboundChannel;
    Channel outboundChannel;

    public HttpConnectHandler(ProxyConnector connector) {
        super(connector);
        this.queue = new ArrayDeque<>(4);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            if (logger.isDebugEnabled()) {
                logger.debug(ctx.channel() + " Accept request: {}", httpRequest);
            }
            InetSocketAddress remoteAddress = getRemoteAddress(httpRequest);
            if (this.remoteAddress == null) {
                this.remoteAddress = remoteAddress;
                doConnect(ctx, httpRequest);
            } else if (this.remoteAddress.equals(remoteAddress)) {
                ctx.fireChannelRead(httpRequest);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug(ctx.channel() + " Switch the remote address [{}] to [{}]",
                        this.remoteAddress, remoteAddress);
                }
                this.remoteAddress = remoteAddress;
                inboundChannel.pipeline().remove(RelayHandler.class);
                outboundChannel.pipeline().remove(RelayHandler.class);
                outboundChannel.writeAndFlush(ctx.alloc().buffer(0))
                    .addListener(ChannelFutureListener.CLOSE);
                doConnect(ctx, httpRequest);
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

    public void doConnect(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        this.connected = false;
        inboundChannel = ctx.channel();
        inboundChannel.config().setAutoRead(false);
        final ChannelPipeline inboundPipeline = inboundChannel.pipeline();
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener((GenericFutureListener<Future<Channel>>) connect -> {
            if (connect.isSuccess()) {
                outboundChannel = connect.getNow();
                ChannelPipeline outboundPipeline = outboundChannel.pipeline();
                if (isSsl) {
                    outboundPipeline.addLast(getClientSslContext().newHandler(outboundChannel.alloc()));
                }
                outboundPipeline.addLast(new HttpRequestEncoder());
                outboundChannel.writeAndFlush(httpRequest).addListener(future -> {
                    if (future.isSuccess()) {
                        connected = true;
                        outboundPipeline.addLast(new RelayHandler(inboundChannel));
                        inboundPipeline.addLast(new RelayHandler(outboundChannel));
                        clearQueue(ctx);
                        inboundChannel.config().setAutoRead(true);
                    } else {
                        ctx.close();
                        outboundChannel.close();
                        clearQueue(null);
                        logger.error("{} Failed to write http request: {}",
                            outboundPipeline, httpRequest, connect.cause());
                    }
                });
            } else {
                ctx.close();
                clearQueue(null);
                logger.error("{} Failed to connect to remote address [{}]",
                    inboundChannel, remoteAddress, connect.cause());
            }
        });
        connector.channel(remoteAddress, inboundChannel.eventLoop(), promise);
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

    public InetSocketAddress getRemoteAddress(HttpRequest httpRequest) {
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
            return getRemoteAddress(host, isSsl ? 443 : 80);
        }
    }

    private InetSocketAddress getRemoteAddress(String host, int defaultPort) {
        int index = host.indexOf(':');
        if (index == -1) {
            return InetSocketAddress.createUnresolved(host, defaultPort);
        } else {
            return InetSocketAddress.createUnresolved(host.substring(0, index),
                Integer.parseInt(host.substring(index + 1)));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
        logger.error(cause.getMessage(), cause);
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
