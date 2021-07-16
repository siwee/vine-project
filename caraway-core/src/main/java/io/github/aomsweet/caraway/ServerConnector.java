package io.github.aomsweet.caraway;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.proxy.ProxyHandler;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

/**
 * @author aomsweet
 */
public interface ServerConnector {

    ChannelFuture channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup);

    void switchUpstreamProxy(Supplier<ProxyHandler> upstreamProxySupplier);

}
