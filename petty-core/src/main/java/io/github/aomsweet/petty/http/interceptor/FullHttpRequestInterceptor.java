package io.github.aomsweet.petty.http.interceptor;

import io.github.aomsweet.petty.HandlerNames;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @author aomsweet
 */
public abstract class FullHttpRequestInterceptor extends FullHttpMessageInterceptor<FullHttpRequestInterceptor>
    implements HttpRequestInterceptor {

    public FullHttpRequestInterceptor() {
        super();
    }

    public FullHttpRequestInterceptor(int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    public final boolean preHandle(Channel clientChannel, HttpRequest httpRequest) throws Exception {
        ChannelPipeline pipeline = clientChannel.pipeline();
        if (httpRequest instanceof FullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) httpRequest;
            pipeline.remove(HandlerNames.DECOMPRESS);
            pipeline.remove(HandlerNames.AGGREGATOR);
            return preHandle(clientChannel, fullHttpRequest);
        } else {
            pipeline
                .addAfter(HandlerNames.DECODER, HandlerNames.DECOMPRESS, new HttpContentDecompressor())
                .addAfter(HandlerNames.DECOMPRESS, HandlerNames.AGGREGATOR, new HttpObjectAggregator(maxContentLength))
                .fireChannelRead(httpRequest);
            return false;
        }
    }

    public abstract boolean preHandle(Channel clientChannel, FullHttpRequest httpRequest) throws Exception;

}
