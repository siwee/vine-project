package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public interface ServerConnector {

    Future<Channel> channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup);

    Future<Channel> channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup, Promise<Channel> promise);
}
