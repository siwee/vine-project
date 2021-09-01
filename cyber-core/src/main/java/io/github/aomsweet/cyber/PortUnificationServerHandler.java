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
package io.github.aomsweet.cyber;

import io.github.aomsweet.cyber.http.HttpAuthorizationHandler;
import io.github.aomsweet.cyber.socks.Socks4ClientRelayHandler;
import io.github.aomsweet.cyber.socks.Socks5ClientRelayHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
@ChannelHandler.Sharable
public class PortUnificationServerHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PortUnificationServerHandler.class);

    CyberServer cyber;
    HttpAuthorizationHandler httpAuthorizationHandler;

    public PortUnificationServerHandler(CyberServer cyber) {
        this.cyber = cyber;
        this.httpAuthorizationHandler = new HttpAuthorizationHandler(cyber);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf in = (ByteBuf) msg;
            final int readerIndex = in.readerIndex();
            if (in.writerIndex() == readerIndex) {
                return;
            }
            ChannelPipeline pipeline = ctx.pipeline().remove(this);
            final byte version = in.getByte(readerIndex);
            if (version == 4) {
                logKnownVersion(ctx, version);
                pipeline.addLast(HandlerNames.DECODER, new Socks4ServerDecoder());
                pipeline.addLast(HandlerNames.RESPONSE_ENCODER, Socks4ServerEncoder.INSTANCE);
                pipeline.addLast(HandlerNames.RELAY, new Socks4ClientRelayHandler(cyber));
            } else if (version == 5) {
                logKnownVersion(ctx, version);
                pipeline.addLast(HandlerNames.DECODER, new Socks5InitialRequestDecoder());
                pipeline.addLast(HandlerNames.RESPONSE_ENCODER, Socks5ServerEncoder.DEFAULT);
                pipeline.addLast(HandlerNames.RELAY, new Socks5ClientRelayHandler(cyber));
            } else {
                pipeline.addLast(HandlerNames.DECODER, new HttpRequestDecoder());
                pipeline.addLast(httpAuthorizationHandler);
            }
            pipeline.fireChannelRead(msg);
        } else {
            ctx.close();
            ReferenceCountUtil.release(msg);
        }
    }

    private static void logKnownVersion(ChannelHandlerContext ctx, byte version) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} Protocol version: {}({})", ctx.channel(), SocksVersion.valueOf(version));
        }
    }
}
