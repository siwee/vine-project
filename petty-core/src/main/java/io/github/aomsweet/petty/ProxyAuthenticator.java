package io.github.aomsweet.petty;

/**
 * @author aomsweet
 */
public interface ProxyAuthenticator {

    boolean authenticate(String username, String password);

}
