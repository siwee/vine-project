package io.github.aomsweet.petty.http;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @author aomsweet
 */
public interface HttpRequestInterceptor {

    default void beforeSend(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest) throws Exception {
    }

    default void beforeSend(Channel clientChannel, Channel serverChannel, HttpContent httpContent) throws Exception {
    }

    default void afterSend(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest) throws Exception {
    }

    default void afterSend(Channel clientChannel, Channel serverChannel, HttpContent httpContent) throws Exception {
    }
}
