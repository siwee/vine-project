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
package io.github.aomsweet.cyber.socks;

import io.github.aomsweet.cyber.ClientRelayHandler;
import io.github.aomsweet.cyber.CyberServer;
import io.github.aomsweet.cyber.HandlerNames;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v4.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public final class Socks4ClientRelayHandler extends ClientRelayHandler<Socks4CommandRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(Socks4ClientRelayHandler.class);

    public static final DefaultSocks4CommandResponse SUCCESS_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS);
    public static final DefaultSocks4CommandResponse REJECTED_OR_FAILED_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);

    public Socks4ClientRelayHandler(CyberServer cyber) {
        super(cyber, logger);
    }

    @Override
    public void channelRead0(Object msg) throws Exception {
        if (msg instanceof Socks4CommandRequest) {
            Socks4CommandRequest request = (Socks4CommandRequest) msg;
            if (request.type() == Socks4CommandType.CONNECT) {
                serverAddress = InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
                doConnectServer((Socks4CommandRequest) msg);
            } else {
                logger.error("Unsupported Socks4 {} command.", request.type());
                close();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    protected void onConnected(Socks4CommandRequest request) {
        clientChannel.writeAndFlush(SUCCESS_RESPONSE);
        ChannelPipeline pipeline = clientChannel.pipeline();
        pipeline.remove(HandlerNames.DECODER);
        pipeline.remove(Socks4ServerEncoder.INSTANCE);
        doServerRelay();
    }

    @Override
    protected void onConnectFailed(Socks4CommandRequest request) {
        ctx.writeAndFlush(REJECTED_OR_FAILED_RESPONSE).addListener(future -> close());
    }
}
