package io.github.aomsweet.caraway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author aomsweet
 */
public class HttpsMitmConnectHandler extends HttpTunnelConnectHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpsMitmConnectHandler.class);

    public static SslContext clientSslContext;
    public static SslContext serverSslContext;

    boolean connected;
    boolean sslHandshakeCompleted;
    Channel clientChannel;
    Channel serverChannel;
    final Queue<Object> queue;

    public HttpsMitmConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
        this.queue = new ArrayDeque<>(2);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if (logger.isDebugEnabled()) {
                logger.debug(ctx.channel() + " Accept request: {}", request);
            }
            if (HttpMethod.CONNECT.equals(request.method())) {
                byte[] bytes = HttpTunnelConnectHandler.ESTABLISHED_BYTES;
                ByteBuf byteBuf = ctx.alloc().buffer(bytes.length);
                ctx.writeAndFlush(byteBuf.writeBytes(bytes));
                ctx.pipeline().addFirst(getServerSslContext().newHandler(ctx.alloc()));
                doConnectServer(ctx, ctx.channel(), request);
            } else {
                queue.offer(msg);
            }
        } else if (msg instanceof HttpContent) {
            if (sslHandshakeCompleted) {
                queue.offer(msg);
            } else {
                ReferenceCountUtil.release(msg);
            }
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
            flush();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            if (((SslHandshakeCompletionEvent) evt).isSuccess()) {
                sslHandshakeCompleted = true;
                tryDucking(ctx);
            } else {
                ctx.close();
                if (serverChannel != null) {
                    ChannelUtils.closeOnFlush(serverChannel);
                }
            }
        }
    }

    @Override
    void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, HttpRequest request) {
        connected = true;
        this.clientChannel = clientChannel;
        this.serverChannel = serverChannel;

        try {
            ChannelPipeline pipeline = serverChannel.pipeline();
            pipeline.addLast(getClientSslContext().newHandler(serverChannel.alloc()));
            pipeline.addLast(new HttpRequestEncoder());
            tryDucking(ctx);
        } catch (SSLException e) {
            logger.error(e.getMessage(), e);
            release(clientChannel, serverChannel);
        }
    }

    void tryDucking(ChannelHandlerContext ctx) {
        if (connected && sslHandshakeCompleted) {
            ctx.pipeline().remove(this);
            if (relayDucking(clientChannel, serverChannel)) {
                flush(ctx);
            } else {
                release(clientChannel, serverChannel);
            }
        }
    }

    @Override
    void failConnect(ChannelHandlerContext ctx, Channel clientChannel, HttpRequest request) {
        ChannelUtils.closeOnFlush(clientChannel);
        flush();
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
                ctx.pipeline().fireChannelRead(message);
            }
        }
    }

    public static SslContext getClientSslContext() throws SSLException {
        if (clientSslContext == null) {
            synchronized (HttpsMitmConnectHandler.class) {
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

    public static SslContext getServerSslContext() throws Exception {
        if (serverSslContext == null) {
            synchronized (HttpsMitmConnectHandler.class) {
                if (serverSslContext == null) {
                    ClassLoader cl = HttpsMitmConnectHandler.class.getClassLoader();
                    try (InputStream caInputStream = cl.getResourceAsStream("caraway/cert/ca.crt");
                         InputStream privateInputStream = cl.getResourceAsStream("caraway/cert/ca_private.key")) {
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(caInputStream);

                        byte[] bytes = privateInputStream.readAllBytes();
                        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(bytes);
                        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

                        serverSslContext = SslContextBuilder.forServer(privateKey, cert).build();
                    }
                }
            }
        }
        return serverSslContext;
    }
}
