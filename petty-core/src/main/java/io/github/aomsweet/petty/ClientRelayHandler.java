package io.github.aomsweet.petty;

import io.github.aomsweet.petty.auth.Credentials;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author aomsweet
 */
public abstract class ClientRelayHandler<Q> extends RelayHandler {

    protected Status status;
    protected Credentials credentials;
    protected InetSocketAddress serverAddress;

    public ClientRelayHandler(PettyServer petty, InternalLogger logger) {
        super(petty, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (status == Status.CONNECTED) {
            relay(ctx, msg);
        } else {
            channelRead0(ctx, msg);
        }
    }

    public abstract void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception;

    protected void doConnectServer(ChannelHandlerContext ctx, Channel clientChannel, Q request) {
        try {
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
                    relayChannel = channelFuture.channel();
                    if (clientChannel.isActive()) {
                        onConnected(ctx, clientChannel, request);
                    } else {
                        release(ctx);
                    }
                } else {
                    logger.error("Unable to establish a remote connection.", future.cause());
                    this.onConnectFailed(ctx, clientChannel, request);
                }
            });
        } catch (ResolveServerAddressException e) {
            logger.error("Unable to get remote address.", e);
            release(ctx);
        } catch (Exception e) {
            e.printStackTrace();
            release(ctx);
        }
    }

    public void addServerRelayHandler(ChannelHandlerContext ctx) {
        if (relayChannel.isActive()) {
            status = Status.CONNECTED;
            relayChannel.pipeline().addLast(HandlerNames.RELAY, new ServerRelayHandler(petty, ctx.channel()));
            System.out.println(relayChannel.pipeline());
        } else {
            release(ctx);
        }
    }

    protected abstract void onConnected(ChannelHandlerContext ctx, Channel clientChannel, Q request);

    protected abstract void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, Q request);

    public enum Status {

        UNCONNECTED, CONNECTED

    }
}
