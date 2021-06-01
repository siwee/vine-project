/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.github.aomsweet.caraway;

import io.netty.channel.*;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends ConnectHandler {

    public SocksServerConnectHandler(CarawayServer caraway) {
        super(caraway);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Socks4CommandRequest) {
            socks4Handle(ctx, (Socks4CommandRequest) msg);
        } else if (msg instanceof Socks5CommandRequest) {
            socks5Handle(ctx, (Socks5CommandRequest) msg);
        } else {
            ctx.close();
        }
    }

    public void socks4Handle(ChannelHandlerContext ctx, Socks4CommandRequest request) {
        final Channel inboundChannel = ctx.channel();
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener((FutureListener<Channel>) future -> {
            final Channel outboundChannel = future.getNow();
            if (future.isSuccess()) {
                ChannelFuture responseFuture = ctx.channel().writeAndFlush(
                    new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS));
                responseFuture.addListener((ChannelFutureListener) channelFuture -> {
                    ctx.pipeline().remove(SocksServerConnectHandler.this);
                    outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                    ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                });
            } else {
                Object response = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);
                ChannelUtils.closeOnFlush(inboundChannel, response);
            }
        });
        InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
        connector.channel(remoteAddress, inboundChannel.eventLoop(), promise).addListener(future -> {
            if (!future.isSuccess()) {
                Object response = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);
                ChannelUtils.closeOnFlush(inboundChannel, response);
            }
        });
    }

    public void socks5Handle(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        final Channel inboundChannel = ctx.channel();
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener((FutureListener<Channel>) future -> {
            final Channel outboundChannel = future.getNow();
            if (future.isSuccess()) {
                ChannelFuture responseFuture = inboundChannel.writeAndFlush(new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS,
                    request.dstAddrType(),
                    request.dstAddr(),
                    request.dstPort()));
                responseFuture.addListener((ChannelFutureListener) channelFuture -> {
                    ctx.pipeline().remove(SocksServerConnectHandler.this);
                    outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                    ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                });
            } else {
                Object response = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType());
                ChannelUtils.closeOnFlush(inboundChannel, response);
            }
        });

        InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
        connector.channel(remoteAddress, inboundChannel.eventLoop(), promise).addListener(future -> {
            if (!future.isSuccess()) {
                Object response = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType());
                ChannelUtils.closeOnFlush(inboundChannel, response);
            }
        });
    }
}
