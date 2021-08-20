package io.github.aomsweet.petty.http;

import io.github.aomsweet.petty.*;
import io.github.aomsweet.petty.auth.Credentials;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.Queue;

/**
 * @author aomsweet
 */
public abstract class HttpClientRelayHandler extends ClientRelayHandler<HttpRequest> {

    public static final byte[] UNAUTHORIZED_RESPONSE = "HTTP/1.1 407 Unauthorized\r\nProxy-Authenticate: Basic realm=\"Access to the staging site\"\r\n\r\n".getBytes();
    public static final byte[] TUNNEL_ESTABLISHED_RESPONSE = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes();

    protected HttpRequest currentHttpRequest;
    protected Queue<HttpRequestInterceptor> requestInterceptors;
    protected Queue<HttpResponseInterceptor> responseInterceptors;

    public HttpClientRelayHandler(PettyServer petty, InternalLogger logger) {
        super(petty, logger);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            if (httpRequest.decoderResult().isSuccess()) {
                if (!preHandle(ctx, httpRequest)) {
                    return;
                }
                handleHttpRequest(ctx, httpRequest);
            } else {
                release(ctx);
            }
        } else if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            if (httpContent.decoderResult().isSuccess()) {
                handleHttpContent(ctx, httpContent);
            } else {
                release(ctx);
            }
        } else {
            handleUnknownMessage(ctx, msg);
        }
    }

    private boolean preHandle(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
        HttpInterceptorManager interceptorManager = petty.getHttpInterceptorManager();
        if (interceptorManager != null) {
            if (requestInterceptors == null) {
                requestInterceptors = interceptorManager.matchRequestInterceptor(httpRequest);
            }
            if (httpRequest.method() == HttpMethod.CONNECT) {
                return true;
            }
            if (responseInterceptors == null) {
                responseInterceptors = interceptorManager.matchResponseInterceptor(httpRequest);
                if (responseInterceptors != null) {
                    this.currentHttpRequest = httpRequest;

                    // if (state == State.READY) {
                    //     ChannelPipeline serverPipeline = relayChannel.pipeline();
                    //     ChannelHandler relayHandler = serverPipeline.get(HandlerNames.RELAY);
                    //     if (relayHandler.getClass() == ServerRelayHandler.class) {
                    //         serverPipeline.replace(HandlerNames.RELAY, HandlerNames.RELAY, newServerRelayHandler(petty, ctx.channel(), relayChannel));
                    //     } else {
                    //         serverPipeline.addBefore(HandlerNames.RELAY, HandlerNames.DECODER, new HttpResponseDecoder());
                    //         ctx.pipeline().addLast(HandlerNames.RESPONSE_ENCODER, new HttpResponseEncoder());
                    //     }
                    // }

                }
            }
            if (requestInterceptors != null) {
                for (HttpRequestInterceptor interceptor = requestInterceptors.peek();
                     interceptor != null; interceptor = requestInterceptors.peek()) {
                    if (interceptor.preHandle(ctx.channel(), httpRequest)) {
                        requestInterceptors.poll();
                    } else {
                        return false;
                    }
                }
                requestInterceptors = null;
            }
        }
        return true;
    }

    public abstract void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception;

    public abstract void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) throws Exception;

    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) throws Exception {
        ctx.fireChannelRead(message);
    }

    public boolean authorize(ChannelHandlerContext ctx, Credentials credentials) {
        ProxyAuthenticator authenticator = petty.getProxyAuthenticator();
        boolean authorized = authenticator == null || authenticator.authenticate(credentials.getUsername(), credentials.getPassword());
        if (!authorized) {
            ByteBuf byteBuf = ctx.alloc().buffer(UNAUTHORIZED_RESPONSE.length);
            ctx.writeAndFlush(byteBuf.writeBytes(UNAUTHORIZED_RESPONSE)).addListener(ChannelFutureListener.CLOSE);
        }
        return authorized;
    }

    public ChannelFuture writeAuthFailedResponse(ChannelHandlerContext ctx, byte[] response) {
        ByteBuf byteBuf = ctx.alloc().buffer(response.length);
        return ctx.writeAndFlush(byteBuf.writeBytes(response));
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

    protected Credentials resolveCredentials(HttpRequest request) {
        HttpHeaders headers = request.headers();
        String authorization = headers.get(HttpHeaderNames.PROXY_AUTHORIZATION);
        if (authorization == null || authorization.isEmpty()) {
            return null;
        } else {
            int i = authorization.indexOf(' ');
            String token = i > -1 && ++i < authorization.length()
                ? authorization.substring(i) : authorization;
            String decode = new String(Base64.getDecoder().decode(token));
            i = decode.indexOf(':');
            if (i > -1) {
                return new Credentials(decode.substring(0, i), decode.substring(++i));
            } else {
                return new Credentials(null, decode);
            }
        }
    }

    public String getHttpRequestInitialLine(HttpRequest httpRequest) {
        return httpRequest.method().name() + ' ' + httpRequest.uri() + ' ' + httpRequest.protocolVersion();
    }
}
