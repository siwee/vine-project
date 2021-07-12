package io.github.aomsweet.caraway.http.mitm;

import io.netty.handler.ssl.SslContext;

/**
 * @author aomsweet
 */
public interface MitmManager {

    SslContext serverSslContext(String... hosts);

}
