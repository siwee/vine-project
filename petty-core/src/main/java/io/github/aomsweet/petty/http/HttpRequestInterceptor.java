package io.github.aomsweet.petty.http;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @author aomsweet
 */
public interface HttpRequestInterceptor {

    boolean match(HttpRequest httpRequest);

    default void preHandle(Channel clientChannel, HttpRequest httpRequest) throws Exception {
    }

    default void preHandle(Channel clientChannel, HttpContent httpContent) throws Exception {
    }

}
