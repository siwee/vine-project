package io.github.aomsweet.caraway;

import io.github.aomsweet.caraway.auth.Credentials;
import io.netty.handler.proxy.ProxyHandler;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * @author aomsweet
 */
public interface ChainedProxyManager<T> {

    Queue<Supplier<ProxyHandler>> lookupChainedProxies(T request,
                                                       Credentials credentials,
                                                       InetSocketAddress clientAddress,
                                                       InetSocketAddress serverAddress);

}
