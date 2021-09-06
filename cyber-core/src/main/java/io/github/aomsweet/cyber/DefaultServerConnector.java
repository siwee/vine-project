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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public class DefaultServerConnector implements ServerConnector {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(DefaultServerConnector.class);

    Bootstrap bootstrap;

    public DefaultServerConnector() {
        this.bootstrap = bootstrap();
    }

    public DefaultServerConnector(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public Bootstrap bootstrap() {
        return bootstrap(channelInitializer());
    }

    public Bootstrap bootstrap(ChannelInitializer<Channel> channelInitializer) {
        return new Bootstrap()
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(channelInitializer);
    }

    public ChannelInitializer<Channel> channelInitializer() {
        return channelInitializer(null);
    }

    public ChannelInitializer<Channel> channelInitializer(ChannelHandler proxyHandler) {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                if (logger.isTraceEnabled()) {
                    ch.pipeline().addLast(new LoggingHandler(LogLevel.TRACE));
                }
                if (proxyHandler != null) {
                    ch.pipeline().addLast(proxyHandler);
                }
            }
        };
    }

    @Override
    public ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx) {
        return bootstrap.clone(ctx.channel().eventLoop()).connect(socketAddress);
    }

    @Override
    public ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx, UpstreamProxy upstreamProxy) {
        if (upstreamProxy == null) {
            return channel(socketAddress, ctx);
        } else {
            ChannelHandler proxyHandler = upstreamProxy.newProxyHandler();
            ChannelInitializer<Channel> initHandler = channelInitializer(proxyHandler);
            return bootstrap.clone(ctx.channel().eventLoop()).handler(initHandler).connect(socketAddress);
        }
    }

    /*
    #####################################################################################
    ################################## Getter | Setter ##################################
    #####################################################################################
     */

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public DefaultServerConnector setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
        return this;
    }

}
