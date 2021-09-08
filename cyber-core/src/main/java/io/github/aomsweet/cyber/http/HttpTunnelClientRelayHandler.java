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
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class HttpTunnelClientRelayHandler extends BasicHttpClientRelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelClientRelayHandler.class);

    /**
     * tls client hello request
     */
    Object clientHello;

    public HttpTunnelClientRelayHandler(CyberServer cyber) {
        super(cyber, logger);
    }

    @Override
    public void handleHttpRequest(HttpRequest httpRequest) throws Exception {
        ctx.pipeline().remove(HandlerNames.DECODER);
        serverAddress = resolveServerAddress(httpRequest);
        ByteBuf byteBuf = ctx.alloc().buffer(TUNNEL_ESTABLISHED_RESPONSE.length);
        ctx.writeAndFlush(byteBuf.writeBytes(TUNNEL_ESTABLISHED_RESPONSE));
        doConnectServer(httpRequest);
    }

    @Override
    public void handleHttpContent(HttpContent httpContent) throws Exception {
        ReferenceCountUtil.release(httpContent);
    }

    @Override
    public void handleUnknownMessage(Object message) {
        clientHello = message;
    }

    @Override
    protected void onConnected(HttpRequest request) {
        doServerRelay();
        if (clientHello != null) {
            relayChannel.writeAndFlush(clientHello);
            clientHello = null;
        }
    }

    @Override
    public void release() {
        if (clientHello != null) {
            ReferenceCountUtil.release(clientHello);
            clientHello = null;
        }
        super.release();
    }
}
