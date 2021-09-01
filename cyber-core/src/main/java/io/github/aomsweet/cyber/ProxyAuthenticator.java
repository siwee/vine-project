package io.github.aomsweet.cyber;

/**
 * @author aomsweet
 */
public interface ProxyAuthenticator {

    boolean authenticate(String username, String password);

}
