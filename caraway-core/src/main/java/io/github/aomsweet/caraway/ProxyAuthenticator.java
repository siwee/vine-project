package io.github.aomsweet.caraway;

import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;

/**
 * @author aomsweet
 */
public interface ProxyAuthenticator {

    default boolean authenticate(String authorization) {
        if (authorization == null || authorization.isEmpty()) {
            return false;
        } else {
            int i = authorization.indexOf(' ');
            String token = i > -1 && ++i < authorization.length()
                ? authorization.substring(i) : authorization;
            i = token.indexOf(':');
            if (i > -1) {
                return authenticate(token.substring(0, i), token.substring(++i));
            } else {
                return authenticate(null, token);
            }
        }
    }

    default boolean authenticate(Socks5PasswordAuthRequest authRequest) {
        return authenticate(authRequest.username(), authRequest.password());
    }

    boolean authenticate(String username, String password);
}
