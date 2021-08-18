package io.github.aomsweet.petty.http;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    public HttpRequestInterceptor matchRequestInterceptor(HttpRequest httpRequest) {
        if (httpRequestInterceptors == null) {
            return null;
        }
        for (HttpRequestInterceptor httpRequestInterceptor : httpRequestInterceptors) {
            if (httpRequestInterceptor.match(httpRequest)) {
                return httpRequestInterceptor;
            }
        }
        return null;
    }

    public HttpResponseInterceptor matchResponseInterceptor(HttpRequest httpRequest, HttpResponse httpResponse) {
        if (httpResponseInterceptors == null) {
            return null;
        }
        for (HttpResponseInterceptor httpResponseInterceptor : httpResponseInterceptors) {
            if (httpResponseInterceptor.match(httpRequest, httpResponse)) {
                return httpResponseInterceptor;
            }
        }
        return null;
    }
}
