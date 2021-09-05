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
package io.github.aomsweet.cyber;

import io.netty.channel.ChannelHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author aomsweet
 */
public class ProxyInfo {

    private String protocol;
    private String host;
    private int port;
    private String username;
    private String password;

    private Supplier<ChannelHandler> proxyHandlerSupplier;

    public ProxyInfo(ProxyType proxyType, String host, int port) {
        this(proxyType, host, port, null, null);
    }

    public ProxyInfo(ProxyType proxyType, String host, int port, String username, String password) {
        this.protocol = proxyType.name().toLowerCase();
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;

        switch (proxyType) {
            case SOCKS5:
                proxyHandlerSupplier = () -> new Socks5ProxyHandler(new InetSocketAddress(host, port), username, password);
                break;
            case SOCKS4a:
                proxyHandlerSupplier = () -> new Socks4ProxyHandler(new InetSocketAddress(host, port));
                break;
            case HTTP:
                if (username == null || password == null) {
                    proxyHandlerSupplier = () -> new HttpProxyHandler(new InetSocketAddress(host, port));
                } else {
                    proxyHandlerSupplier = () -> new HttpProxyHandler(new InetSocketAddress(host, port), username, password);
                }
                break;
            default:
                throw new RuntimeException("UnKnow proxy type.");
        }
    }

    public ProxyInfo(String protocol, String host, int port, Supplier<ChannelHandler> proxyHandlerSupplier) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.proxyHandlerSupplier = proxyHandlerSupplier;
    }

    public ProxyInfo(String protocol, String host, int port, String username, String password, Supplier<ChannelHandler> proxyHandlerSupplier) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.proxyHandlerSupplier = proxyHandlerSupplier;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public ChannelHandler newProxyHandler() {
        return proxyHandlerSupplier.get();
    }

    @Override
    public String toString() {
        if (username == null && password == null) {
            return protocol + "://" + host + ':' + port;
        }
        return protocol + "://" +
            (username == null ? "" : username) + ':' +
            (password == null ? "" : password) + '@' + host + ':' + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProxyInfo proxyInfo = (ProxyInfo) o;

        if (port != proxyInfo.port) return false;
        if (!Objects.equals(protocol, proxyInfo.protocol)) return false;
        if (!Objects.equals(host, proxyInfo.host)) return false;
        if (!Objects.equals(username, proxyInfo.username)) return false;
        return Objects.equals(password, proxyInfo.password);
    }

    @Override
    public int hashCode() {
        int result = protocol != null ? protocol.hashCode() : 0;
        result = 31 * result + (host != null ? host.hashCode() : 0);
        result = 31 * result + port;
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }
}
