package io.github.aomsweet.petty.http.interceptor;

import io.netty.handler.codec.http.HttpRequest;

import java.util.*;

/**
 * @author aomsweet
 */
public class HttpInterceptorManager {

    List<HttpRequestInterceptor> httpRequestInterceptors;
    List<HttpResponseInterceptor> httpResponseInterceptors;

    public HttpInterceptorManager addInterceptor(HttpRequestInterceptor interceptor) {
        Objects.requireNonNull(interceptor);
        if (httpRequestInterceptors == null) {
            httpRequestInterceptors = new ArrayList<>();
        }
        httpRequestInterceptors.add(interceptor);
        return this;
    }

    public HttpInterceptorManager addInterceptor(HttpResponseInterceptor interceptor) {
        Objects.requireNonNull(interceptor);
        if (httpResponseInterceptors == null) {
            httpResponseInterceptors = new ArrayList<>();
        }
        httpResponseInterceptors.add(interceptor);
        return this;
    }

    public Queue<HttpRequestInterceptor> matchRequestInterceptor(HttpRequest httpRequest) {
        if (httpRequestInterceptors == null) {
            return null;
        }
        Queue<HttpRequestInterceptor> queue = null;
        for (HttpRequestInterceptor httpRequestInterceptor : httpRequestInterceptors) {
            if (httpRequestInterceptor.match(httpRequest)) {
                if (queue == null) {
                    queue = new ArrayDeque<>(2);
                }
                queue.offer(httpRequestInterceptor);
            }
        }
        return queue;
    }

    public Queue<HttpResponseInterceptor> matchResponseInterceptor(HttpRequest httpRequest) {
        if (httpResponseInterceptors == null) {
            return null;
        }
        Queue<HttpResponseInterceptor> queue = null;
        for (HttpResponseInterceptor httpResponseInterceptor : httpResponseInterceptors) {
            if (httpResponseInterceptor.match(httpRequest)) {
                if (queue == null) {
                    queue = new ArrayDeque<>(2);
                }
                queue.offer(httpResponseInterceptor);
            }
        }
        return queue;
    }
}
