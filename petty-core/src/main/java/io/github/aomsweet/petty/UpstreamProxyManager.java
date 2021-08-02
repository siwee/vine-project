package io.github.aomsweet.petty;

import io.github.aomsweet.petty.auth.Credentials;
import io.netty.handler.proxy.ProxyHandler;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * @author aomsweet
 */
public interface UpstreamProxyManager<T> {

    Queue<Supplier<ProxyHandler>> lookupChainedProxies(T request,
                                                       Credentials credentials,
                                                       InetSocketAddress clientAddress,
                                                       InetSocketAddress serverAddress);

}
