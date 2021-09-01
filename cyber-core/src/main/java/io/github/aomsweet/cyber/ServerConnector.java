package io.github.aomsweet.cyber;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author aomsweet
 */
public interface ServerConnector {

    ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx);

    ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx, List<ProxyInfo> upstreamProxies);

}
