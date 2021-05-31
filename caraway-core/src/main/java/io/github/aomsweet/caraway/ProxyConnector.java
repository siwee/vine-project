package io.github.aomsweet.caraway;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public abstract class ProxyConnector {

    Bootstrap bootstrap;

    public ProxyConnector(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    abstract Future<Channel> channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup);

    abstract Future<Channel> channel(InetSocketAddress socketAddress, EventLoopGroup eventLoopGroup, Promise<Channel> promise);
}
