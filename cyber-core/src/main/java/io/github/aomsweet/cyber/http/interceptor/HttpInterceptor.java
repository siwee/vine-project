package io.github.aomsweet.cyber.http.interceptor;

import io.netty.handler.codec.http.HttpRequest;

/**
 * @author aomsweet
 */
public interface HttpInterceptor {

    boolean match(HttpRequest httpRequest);

    default HttpRequestInterceptor requestInterceptor() {
        return null;
    }

    default HttpResponseInterceptor responseInterceptor() {
        return null;
    }
}
