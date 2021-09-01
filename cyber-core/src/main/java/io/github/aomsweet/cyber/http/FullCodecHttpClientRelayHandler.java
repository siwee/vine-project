package io.github.aomsweet.cyber.http;

import io.github.aomsweet.cyber.HandlerNames;
import io.github.aomsweet.cyber.CyberServer;
import io.github.aomsweet.cyber.ServerRelayHandler;
import io.github.aomsweet.cyber.http.interceptor.HttpResponseInterceptor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
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
public abstract class FullCodecHttpClientRelayHandler extends BasicHttpClientRelayHandler {

    protected boolean isSsl;
    protected Queue<Object> httpMessages;

    public FullCodecHttpClientRelayHandler(CyberServer cyber, InternalLogger logger) {
        super(cyber, logger);
        this.httpMessages = new ArrayDeque<>(4);
    }

    @Override
    protected void onConnected(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) throws Exception {
        ChannelPipeline pipeline = relayChannel.pipeline();
        if (isSsl) {
            SslContext clientSslContext = getClientSslContext();
            pipeline.addLast(HandlerNames.SSL, clientSslContext.newHandler(relayChannel.alloc(),
                serverAddress.getHostName(), serverAddress.getPort()));
        }
        pipeline.addLast(HandlerNames.REQUEST_ENCODER, new HttpRequestEncoder());
        doServerRelay(ctx);
    }

    @Override
    public void doServerRelay(ChannelHandlerContext ctx) {
        super.doServerRelay(ctx);
        for (Object message = httpMessages.poll(); message != null; message = httpMessages.poll()) {
            relayChannel.writeAndFlush(message);
        }
    }

    @Override
    public void destroy(ChannelHandlerContext ctx) {
        for (Object message = httpMessages.poll(); message != null; message = httpMessages.poll()) {
            ReferenceCountUtil.release(message);
        }
        super.destroy(ctx);
    }

    @Override
    public ChannelHandler newServerRelayHandler(ChannelHandlerContext ctx) {
        if (cyber.getHttpInterceptorManager() == null) {
            return new ServerRelayHandler(cyber, ctx.channel());
        } else {
            return newInterceptedServerRelayHandler(ctx);
        }
    }

    protected ChannelHandler newInterceptedServerRelayHandler(ChannelHandlerContext ctx) {
        Channel clientChannel = ctx.channel();
        Channel serverChannel = relayChannel;
        ChannelPipeline clientPipeline = clientChannel.pipeline();
        ChannelPipeline serverPipeline = serverChannel.pipeline();

        serverPipeline.addLast(HandlerNames.DECODER, new HttpResponseDecoder());
        if (clientPipeline.get(HandlerNames.RESPONSE_ENCODER) == null) {
            clientPipeline.addLast(HandlerNames.RESPONSE_ENCODER, new HttpResponseEncoder());
        }

        return new ServerRelayHandler(cyber, clientChannel) {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (responseInterceptors == null) {
                    super.channelRead(ctx, msg);
                } else if (msg instanceof HttpResponse) {
                    HttpResponse httpResponse = (HttpResponse) msg;
                    for (HttpResponseInterceptor interceptor = responseInterceptors.peek(); interceptor != null; interceptor = responseInterceptors.peek()) {
                        if (interceptor.preHandle(clientChannel, serverChannel, currentRequest, httpResponse)) {
                            responseInterceptors.poll();
                        } else {
                            return;
                        }
                    }
                    currentRequest = null;
                    responseInterceptors = null;
                    super.channelRead(ctx, msg);
                } else {
                    super.channelRead(ctx, msg);
                }
            }
        };
    }

    public SslContext getClientSslContext() throws SSLException {
        SslContext clientSslContext;
        if ((clientSslContext = cyber.getClientSslContext()) == null) {
            synchronized (cyber) {
                if ((clientSslContext = cyber.getClientSslContext()) == null) {
                    //https://github.com/GlowstoneMC/Glowstone/blob/5b89f945b4/src/main/java/net/glowstone/net/http/HttpClient.java
                    clientSslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                    cyber.setClientSslContext(clientSslContext);
                }
            }
        }
        return clientSslContext;
    }
}
