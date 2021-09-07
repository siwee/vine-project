/*
  Copyright 2021 The Cyber Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.github.aomsweet.cyber.http;

import io.github.aomsweet.cyber.ClientRelayHandler;
import io.github.aomsweet.cyber.CyberServer;
import io.github.aomsweet.cyber.ResolveServerAddressException;
import io.github.aomsweet.cyber.http.interceptor.HttpInterceptorManager;
import io.github.aomsweet.cyber.http.interceptor.HttpRequestInterceptor;
import io.github.aomsweet.cyber.http.interceptor.HttpResponseInterceptor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;
import java.util.Queue;

/**
 * @author aomsweet
 */
public abstract class BasicHttpClientRelayHandler extends ClientRelayHandler<HttpRequest> {

    public static final byte[] TUNNEL_ESTABLISHED_RESPONSE = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes();

    protected HttpRequest currentRequest;
    protected Queue<HttpRequestInterceptor> requestInterceptors;
    protected Queue<HttpResponseInterceptor> responseInterceptors;

    public BasicHttpClientRelayHandler(CyberServer cyber, InternalLogger logger) {
        super(cyber, logger);
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
                close(ctx);
            }
        } else if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            if (httpContent.decoderResult().isSuccess()) {
                handleHttpContent(ctx, httpContent);
            } else {
                close(ctx);
            }
        } else {
            handleUnknownMessage(ctx, msg);
        }
    }

    private boolean preHandle(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
        HttpInterceptorManager interceptorManager = cyber.getHttpInterceptorManager();
        if (interceptorManager != null) {
            if (requestInterceptors == null) {
                requestInterceptors = interceptorManager.matchRequestInterceptor(httpRequest);
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

            if (httpRequest.method() == HttpMethod.CONNECT) {
                return true;
            }
            if (responseInterceptors == null) {
                responseInterceptors = interceptorManager.matchResponseInterceptor(httpRequest);
                if (responseInterceptors != null) {
                    this.currentRequest = httpRequest;
                }
            }
        }
        return true;
    }

    public abstract void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception;

    public abstract void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) throws Exception;

    public void handleUnknownMessage(ChannelHandlerContext ctx, Object message) throws Exception {
        ctx.fireChannelRead(message);
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

    public String getHttpRequestInitialLine(HttpRequest httpRequest) {
        return httpRequest.method().name() + ' ' + httpRequest.uri() + ' ' + httpRequest.protocolVersion();
    }
}
