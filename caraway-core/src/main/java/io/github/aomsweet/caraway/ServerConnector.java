package io.github.aomsweet.caraway;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public interface ServerConnector {

    ChannelFuture channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup);

}
