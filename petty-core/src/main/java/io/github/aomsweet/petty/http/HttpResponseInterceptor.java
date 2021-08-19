package io.github.aomsweet.petty.http;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * @author aomsweet
 */
public interface HttpResponseInterceptor {

    default boolean match(HttpRequest httpRequest) {
        return true;
    }

    boolean preHandle(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, HttpResponse httpResponse) throws Exception;

}
