package io.github.aomsweet.caraway;

import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;

import java.util.Base64;

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
            String decode = new String(Base64.getDecoder().decode(token));
            i = decode.indexOf(':');
            if (i > -1) {
                return authenticate(decode.substring(0, i), decode.substring(++i));
            } else {
                return authenticate(null, decode);
            }
        }
    }

    default boolean authenticate(Socks5PasswordAuthRequest authRequest) {
        return authenticate(authRequest.username(), authRequest.password());
    }

    boolean authenticate(String username, String password);
}
