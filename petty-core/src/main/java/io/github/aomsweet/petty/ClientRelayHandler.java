package io.github.aomsweet.petty;

import io.github.aomsweet.petty.auth.Credentials;
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

    public ClientRelayHandler(PettyServer petty, InternalLogger logger) {
        super(petty, logger);
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
        ServerConnector connector = petty.getConnector();
        UpstreamProxyManager upstreamProxyManager = petty.getUpstreamProxyManager();
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
                state = State.CONNECTED;
                relayChannel = channelFuture.channel();
                if (clientChannel.isActive()) {
                    onConnected(ctx, clientChannel, request);
                }
            } else {
                logger.error("Unable to establish a remote connection.", future.cause());
                this.onConnectFailed(ctx, clientChannel, request);
            }
        });
    }

    public void relayReady(ChannelHandlerContext ctx) {
        if (relayChannel.isActive()) {
            relayChannel.pipeline().addLast(HandlerNames.RELAY, newServerRelayHandler(petty, ctx.channel(), relayChannel));
            state = State.READY;
            // System.err.println("client: " + ctx.pipeline());
            // System.err.println("server: " + relayChannel.pipeline());
        } else {
            release(ctx);
        }
    }

    public ChannelHandler newServerRelayHandler(PettyServer petty, Channel clientChannel, Channel serverChannel) {
        return new ServerRelayHandler(petty, clientChannel);
    }

    protected abstract void onConnected(ChannelHandlerContext ctx, Channel clientChannel, Q request) throws Exception;

    protected void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, Q request) throws Exception {
        release(ctx);
    }

}
