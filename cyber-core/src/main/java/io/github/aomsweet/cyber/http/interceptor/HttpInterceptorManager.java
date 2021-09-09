/*
  Copyright 2021 The Cyber Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.github.aomsweet.cyber.http.interceptor;

import io.netty.handler.codec.http.HttpRequest;

import java.util.*;

/**
 * @author aomsweet
 */
public class HttpInterceptorManager {

    List<HttpInterceptor> httpInterceptors;

    public HttpInterceptorManager addInterceptor(HttpInterceptor interceptor) {
        Objects.requireNonNull(interceptor);
        if (httpInterceptors == null) {
            httpInterceptors = new ArrayList<>();
        }
        httpInterceptors.add(interceptor);
        return this;
    }

    public Queue<HttpInterceptor> matchInterceptor(HttpRequest httpRequest) {
        if (httpInterceptors == null) {
            return null;
        }
        Queue<HttpInterceptor> queue = null;
        for (HttpInterceptor httpInterceptor : httpInterceptors) {
            if (httpInterceptor.match(httpRequest)) {
                if (queue == null) {
                    queue = new ArrayDeque<>(2);
                }
                queue.offer(httpInterceptor);
            }
        }
        return queue;
    }
}
