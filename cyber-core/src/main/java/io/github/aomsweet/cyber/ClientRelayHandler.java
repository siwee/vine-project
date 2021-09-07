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

import io.netty.channel.*;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;
import java.util.Queue;

/**
 * @author aomsweet
 */
public abstract class ClientRelayHandler<R> extends RelayHandler {

    protected Credentials credentials;
    protected UpstreamProxy upstreamProxy;
    protected InetSocketAddress serverAddress;

    protected ChannelManager channelManager;
    protected UpstreamProxyManager upstreamProxyManager;

    public ClientRelayHandler(CyberServer cyber, InternalLogger logger) {
        super(cyber, logger);
        this.channelManager = cyber.channelManager;
        this.upstreamProxyManager = cyber.upstreamProxyManager;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (state == State.READY) {
            relay(ctx, msg);
        } else {
            channelRead0(ctx, msg);
        }
    }

    public abstract void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception;

    protected void doConnectServer(ChannelHandlerContext ctx, Channel clientChannel, R request) throws Exception {
        Queue<? extends UpstreamProxy> upstreamProxies = null;
        if (upstreamProxyManager != null) {
            upstreamProxies = upstreamProxyManager.lookupUpstreamProxies(request, credentials,
                clientChannel.remoteAddress(), serverAddress);
        }

        ChannelFuture future;
        if (upstreamProxies == null || upstreamProxies.isEmpty()) {
            future = channelManager.acquire(serverAddress, ctx);
        } else {
            CompleteChannelPromise promise = new CompleteChannelPromise(ctx.channel().eventLoop());
            doConnectServer(ctx, upstreamProxies, promise);
            future = promise;
        }
        future.addListener(action -> {
            if (action.isSuccess()) {
                try {
                    state = State.CONNECTED;
                    relayChannel = future.channel();
                    if (clientChannel.isActive()) {
                        onConnected(ctx, clientChannel, request);
                    }
                } catch (Exception e) {
                    logger.error("{}: {}", e.getClass().getName(), e.getMessage(), e);
                    close(ctx);
                }
            } else {
                logger.error("Unable to establish a remote connection.", action.cause());
                this.onConnectFailed(ctx, clientChannel, request);
            }
        });
    }

    protected void doConnectServer(ChannelHandlerContext ctx,
                                   Queue<? extends UpstreamProxy> upstreamProxies,
                                   CompleteChannelPromise promise) {
        UpstreamProxy upstreamProxy = upstreamProxies.poll();
        if (logger.isDebugEnabled()) {
            logger.debug("Use upstream proxy: [{}]", upstreamProxy);
        }
        channelManager.acquire(serverAddress, upstreamProxy, ctx).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                this.upstreamProxy = upstreamProxy;
                promise.setChannel(future.channel()).setSuccess();
            } else {
                Throwable cause = future.cause();
                upstreamProxyManager.failConnectExceptionCaught(upstreamProxy, serverAddress, cause);
                if (upstreamProxies.peek() != null) {
                    doConnectServer(ctx, upstreamProxies, promise);
                } else {
                    promise.setFailure(cause);
                }
            }
        });
    }

    public void doServerRelay(ChannelHandlerContext ctx) {
        if (relayChannel.isActive()) {
            relayChannel.pipeline().addLast(HandlerNames.RELAY, newServerRelayHandler(ctx));
            state = State.READY;
        } else {
            close(ctx);
        }
    }

    public ChannelHandler newServerRelayHandler(ChannelHandlerContext ctx) {
        return new ServerRelayHandler(cyber, ctx.channel());
    }

    protected abstract void onConnected(ChannelHandlerContext ctx, Channel clientChannel, R request) throws Exception;

    protected void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, R request) throws Exception {
        close(ctx);
    }

    @Override
    protected void releaseRelayChannel() {
        if (relayChannel == null || !relayChannel.isActive()) {
            return;
        }
        ChannelManager channelManager = cyber.channelManager;
        if (channelManager != null) {
            if (upstreamProxy == null) {
                channelManager.release(relayChannel, serverAddress);
            } else {
                channelManager.release(relayChannel, serverAddress, upstreamProxy);
            }
        }
    }

    public ClientRelayHandler<R> setCredentials(Credentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public ClientRelayHandler<R> setServerAddress(InetSocketAddress serverAddress) {
        this.serverAddress = serverAddress;
        return this;
    }
}
