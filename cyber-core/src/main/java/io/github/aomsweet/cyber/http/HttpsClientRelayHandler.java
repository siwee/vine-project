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

import io.github.aomsweet.cyber.CyberServer;
import io.github.aomsweet.cyber.HandlerNames;
import io.github.aomsweet.cyber.http.mitm.MitmManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class HttpsClientRelayHandler extends FullCodecHttpClientRelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpsClientRelayHandler.class);

    boolean sslHandshakeCompleted;

    public HttpsClientRelayHandler(CyberServer cyber) {
        super(cyber, logger);
        this.isSsl = true;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (state == State.READY && cyber.getHttpInterceptorManager() == null) {
            relay(msg);
        } else {
            channelRead0(msg);
        }
    }

    @Override
    public void handleHttpRequest(HttpRequest request) throws Exception {
        if (HttpMethod.CONNECT.equals(request.method())) {
            this.serverAddress = resolveServerAddress(request);
            ByteBuf byteBuf = ctx.alloc().buffer(TUNNEL_ESTABLISHED_RESPONSE.length);
            ctx.writeAndFlush(byteBuf.writeBytes(TUNNEL_ESTABLISHED_RESPONSE));

            String host = serverAddress.getHostName();
            MitmManager mitmManager = cyber.getMitmManager();
            SslContext sslContext = mitmManager.serverSslContext(host);
            ctx.pipeline().addFirst(HandlerNames.SSL, sslContext.newHandler(ctx.alloc()));

            doConnectServer(request);
        } else if (state == State.READY) {
            relay(request);
        } else {
            httpMessages.offer(request);
        }
    }

    @Override
    public void doServerRelay() {
        if (sslHandshakeCompleted) {
            super.doServerRelay();
        }
    }

    @Override
    public void handleHttpContent(HttpContent httpContent) {
        if (sslHandshakeCompleted) {
            httpMessages.offer(httpContent);
        } else if (state == State.READY) {
            relay(httpContent);
        } else {
            ReferenceCountUtil.release(httpContent);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            if (((SslHandshakeCompletionEvent) evt).isSuccess()) {
                sslHandshakeCompleted = true;
                if (state == State.CONNECTED) {
                    super.doServerRelay();
                }
            }
        }
    }
}
