package io.github.aomsweet.cyber;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author aomsweet
 */
public abstract class ClientRelayHandler<Q> extends RelayHandler {

    protected Credentials credentials;
    protected InetSocketAddress serverAddress;

    public ClientRelayHandler(CyberServer cyber, InternalLogger logger) {
        super(cyber, logger);
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

    protected void doConnectServer(ChannelHandlerContext ctx, Channel clientChannel, Q request) throws Exception {
        ServerConnector connector = cyber.getConnector();
        UpstreamProxyManager upstreamProxyManager = cyber.getUpstreamProxyManager();
        List<ProxyInfo> proxyHandlers = null;
        if (upstreamProxyManager != null) {
            proxyHandlers = upstreamProxyManager.lookupUpstreamProxies(request, credentials,
                clientChannel.remoteAddress(), serverAddress);
        }
        ChannelFuture channelFuture = proxyHandlers == null
            ? connector.channel(serverAddress, ctx)
            : connector.channel(serverAddress, ctx, proxyHandlers);
        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                try {
                    state = State.CONNECTED;
                    relayChannel = channelFuture.channel();
                    if (clientChannel.isActive()) {
                        onConnected(ctx, clientChannel, request);
                    }
                } catch (Exception e) {
                    logger.error("{}: {}", e.getClass().getName(), e.getMessage(), e);
                    release(ctx);
                }
            } else {
                logger.error("Unable to establish a remote connection.", future.cause());
                this.onConnectFailed(ctx, clientChannel, request);
            }
        });
    }

    public void doServerRelay(ChannelHandlerContext ctx) {
        if (relayChannel.isActive()) {
            relayChannel.pipeline().addLast(HandlerNames.RELAY, newServerRelayHandler(ctx));
            state = State.READY;
        } else {
            release(ctx);
        }
    }

    public ChannelHandler newServerRelayHandler(ChannelHandlerContext ctx) {
        return new ServerRelayHandler(cyber, ctx.channel());
    }

    protected abstract void onConnected(ChannelHandlerContext ctx, Channel clientChannel, Q request) throws Exception;

    protected void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, Q request) throws Exception {
        release(ctx);
    }

    public ClientRelayHandler<Q> setCredentials(Credentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public ClientRelayHandler<Q> setServerAddress(InetSocketAddress serverAddress) {
        this.serverAddress = serverAddress;
        return this;
    }
}
