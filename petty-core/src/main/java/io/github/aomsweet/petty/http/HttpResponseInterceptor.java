package io.github.aomsweet.petty.http;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * @author aomsweet
 */
public interface HttpResponseInterceptor {

    default void beforeSend(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
    }

    default void beforeSend(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, HttpContent httpContent) throws Exception {
    }

    default void afterSend(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
    }

    default void afterSend(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, HttpContent httpContent) throws Exception {
    }

}
