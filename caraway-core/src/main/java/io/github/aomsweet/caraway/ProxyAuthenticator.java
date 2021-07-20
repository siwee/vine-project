package io.github.aomsweet.caraway;

/**
 * @author aomsweet
 */
public interface ProxyAuthenticator {

    boolean authenticate(String username, String password);

}
