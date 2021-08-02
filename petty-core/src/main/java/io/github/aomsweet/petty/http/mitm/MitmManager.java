package io.github.aomsweet.petty.http.mitm;

import io.netty.handler.ssl.SslContext;

import java.security.cert.X509Certificate;

/**
 * @author aomsweet
 */
public interface MitmManager {

    SslContext serverSslContext(String host) throws Exception;

    X509Certificate getIssuerCertificate();

}
