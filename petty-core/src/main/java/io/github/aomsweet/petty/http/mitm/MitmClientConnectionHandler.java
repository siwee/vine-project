package io.github.aomsweet.petty.http.mitm;

import io.github.aomsweet.petty.*;
import io.github.aomsweet.petty.http.HttpClientConnectionHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author aomsweet
 */
public abstract class MitmClientConnectionHandler extends HttpClientConnectionHandler {

    boolean isSsl;
    boolean connected;

    Channel clientChannel;
    Channel serverChannel;

    InetSocketAddress serverAddress;
    Queue<Object> queue;

    public MitmClientConnectionHandler(PettyServer petty, InternalLogger logger) {
        super(petty, logger);
        this.queue = new ArrayDeque<>(4);
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        try {
            this.clientChannel = ctx.channel();
            handleHttpRequest0(ctx, httpRequest);
        } catch (Exception e) {
            release(clientChannel, serverChannel);
        }
    }

    public abstract void handleHttpRequest0(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception;

    @Override
    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) {
        flush();
        ctx.close();
        ReferenceCountUtil.release(message);
    }

    @Override
    protected void doConnectServer(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        this.connected = false;
        super.doConnectServer(ctx, clientChannel, request);
    }

    @Override
    protected void onConnected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        this.connected = true;
        this.serverChannel = serverChannel;
        try {
            ChannelPipeline pipeline = serverChannel.pipeline();
            if (isSsl) {
                SslContext clientSslContext = getClientSslContext();
                pipeline.addLast(HandlerNames.SSL, clientSslContext.newHandler(serverChannel.alloc(),
                    serverAddress.getHostName(), serverAddress.getPort()));
            }
            pipeline.addLast(HandlerNames.ENCODER, new HttpRequestEncoder());
            doRelayDucking(ctx, request);
        } catch (SSLException e) {
            release(clientChannel, serverChannel);
        }
    }

    @Override
    public boolean relayDucking(Channel clientChannel, Channel serverChannel) {
        if (clientChannel.isActive()) {
            if (serverChannel.isActive()) {
                clientChannel.pipeline().addLast(new ClientConnectionHandler(serverChannel));
                serverChannel.pipeline().addLast(new ServerConnectionHandler(clientChannel));
                return true;
            } else {
                ChannelUtils.closeOnFlush(clientChannel);
            }
        } else {
            ChannelUtils.closeOnFlush(serverChannel);
        }
        return false;
    }

    protected abstract void doRelayDucking(ChannelHandlerContext ctx, HttpRequest request);

    @Override
    protected void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        release(clientChannel, null);
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
                if (ctx.isRemoved()) {
                    ctx.pipeline().fireChannelRead(message);
                } else {
                    ctx.fireChannelRead(message);
                }
            }
        }
    }

    @Override
    public void release(Channel clientChannel, Channel serverChannel) {
        flush(null);
        super.release(clientChannel, serverChannel);
    }

    @Override
    protected InetSocketAddress getServerAddress(HttpRequest request) throws ResolveServerAddressException {
        return serverAddress;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        release(clientChannel, serverChannel);
        logger.error(cause.getMessage(), cause);
    }

    public SslContext getClientSslContext() throws SSLException {
        SslContext clientSslContext;
        if ((clientSslContext = petty.getClientSslContext()) == null) {
            synchronized (petty) {
                if ((clientSslContext = petty.getClientSslContext()) == null) {
                    //https://github.com/GlowstoneMC/Glowstone/blob/5b89f945b4/src/main/java/net/glowstone/net/http/HttpClient.java
                    clientSslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                    petty.setClientSslContext(clientSslContext);
                }
            }
        }
        return clientSslContext;
    }
}
