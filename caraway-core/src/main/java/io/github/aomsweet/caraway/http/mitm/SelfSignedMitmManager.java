package io.github.aomsweet.caraway.http.mitm;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * @author aomsweet
 */
public class SelfSignedMitmManager implements MitmManager {

    protected PrivateKey rootPrivateKey;
    protected X509Certificate rootCertificate;

    private SslContext defaultServerSslContext;

    public SelfSignedMitmManager() throws Exception {
        loadRootCertificate();
        loadDefaultServerSslContext();
    }

    public SelfSignedMitmManager(InputStream certInputStream, InputStream keyInputStream) throws Exception {
        loadRootCertificate(certInputStream, keyInputStream);
        loadDefaultServerSslContext();
    }


    @Override
    public SslContext serverSslContext(String... hosts) {
        return defaultServerSslContext;
    }

    private void loadDefaultServerSslContext() throws SSLException {
        this.defaultServerSslContext = SslContextBuilder.forServer(rootPrivateKey, rootCertificate).build();
    }

    private void loadRootCertificate(InputStream certInputStream, InputStream keyInputStream) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        this.rootCertificate = (X509Certificate) cf.generateCertificate(certInputStream);

        byte[] bytes = keyInputStream.readAllBytes();
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(bytes);
        this.rootPrivateKey = keyFactory.generatePrivate(privateKeySpec);
    }

    private void loadRootCertificate() throws Exception {
        ClassLoader cl = this.getClass().getClassLoader();
        try (InputStream certInputStream = cl.getResourceAsStream("caraway/cert/ca.crt");
             InputStream keyInputStream = cl.getResourceAsStream("caraway/cert/ca_private.key")) {
            loadRootCertificate(certInputStream, keyInputStream);
        }
    }

}
