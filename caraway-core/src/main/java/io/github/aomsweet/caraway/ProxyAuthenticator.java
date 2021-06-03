package io.github.aomsweet.caraway;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;

/**
 * @author aomsweet
 */
public interface ProxyAuthenticator {

    String getRealm();

    default boolean authenticate(Object request) {
        if (request instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) request;
            String authorization = httpRequest.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
            if (authorization == null || authorization.isEmpty()) {
                return authenticate(null, null);
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
        } else if (request instanceof Socks5PasswordAuthRequest) {
            Socks5PasswordAuthRequest authRequest = (Socks5PasswordAuthRequest) request;
            return authenticate(authRequest.username(), authRequest.password());
        } else {
            return false;
        }
    }

    boolean authenticate(String username, String password);
}
