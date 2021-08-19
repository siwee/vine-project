package io.github.aomsweet.petty.http;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @author aomsweet
 */
public interface HttpRequestInterceptor {

    default boolean match(HttpRequest httpRequest) {
        return true;
    }

    boolean preHandle(Channel clientChannel, HttpRequest httpRequest) throws Exception;

}
