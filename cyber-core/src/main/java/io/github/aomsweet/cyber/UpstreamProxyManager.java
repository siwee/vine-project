package io.github.aomsweet.cyber;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

/**
 * @author aomsweet
 */
public interface UpstreamProxyManager {

    List<ProxyInfo> lookupUpstreamProxies(Object requestObject,
                                          Credentials credentials,
                                          SocketAddress clientAddress,
                                          InetSocketAddress serverAddress) throws Exception;

}
