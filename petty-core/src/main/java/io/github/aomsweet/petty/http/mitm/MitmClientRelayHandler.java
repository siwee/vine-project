package io.github.aomsweet.petty.http.mitm;

import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
import io.github.aomsweet.petty.http.HttpClientRelayHandler;
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
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author aomsweet
 */
public abstract class MitmClientRelayHandler extends HttpClientRelayHandler {

    boolean isSsl;
    Queue<Object> queue;

    public MitmClientRelayHandler(PettyServer petty, InternalLogger logger) {
        super(petty, logger);
        this.queue = new ArrayDeque<>(4);
    }

    @Override
    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) {
        release(ctx);
        ReferenceCountUtil.release(message);
    }

    @Override
    protected void onConnected(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        try {
            ChannelPipeline pipeline = relayChannel.pipeline();
            if (isSsl) {
                SslContext clientSslContext = getClientSslContext();
                pipeline.addLast(HandlerNames.SSL, clientSslContext.newHandler(relayChannel.alloc(),
                    serverAddress.getHostName(), serverAddress.getPort()));
            }
            pipeline.addLast(HandlerNames.ENCODER, new HttpRequestEncoder());
            addServerRelayHandler(ctx);
            relay(ctx, request);
            flushQueue(false);
        } catch (SSLException e) {
            release(ctx);
        }
    }

    @Override
    protected void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        release(ctx);
    }

    public void flushQueue(boolean release) {
        do {
            Object message = queue.poll();
            if (release) {
                ReferenceCountUtil.release(message);
            } else {
                relayChannel.writeAndFlush(message);
            }
        } while (!queue.isEmpty());
    }

    @Override
    public void release(ChannelHandlerContext ctx) {
        flushQueue(true);
        super.release(ctx);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        release(ctx);
        super.exceptionCaught(ctx, cause);
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
