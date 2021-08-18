package io.github.aomsweet.petty.http;

import io.github.aomsweet.petty.HandlerNames;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;

/**
 * @author aomsweet
 */
public abstract class FullHttpRequestInterceptor implements HttpRequestInterceptor {

    /**
     * default max content length size is 8MB
     */
    private static final int DEFAULT_MAX_CONTENT_LENGTH = 1024 * 1024 * 8;

    private int maxContentLength;

    public FullHttpRequestInterceptor() {
        this(DEFAULT_MAX_CONTENT_LENGTH);
    }

    public FullHttpRequestInterceptor(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    @Override
    public final void preHandle(Channel clientChannel, HttpRequest httpRequest) throws Exception {
        if (httpRequest instanceof FullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) httpRequest;
            if (fullHttpRequest.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                //TODO Why?
                fullHttpRequest.content().markReaderIndex();
                fullHttpRequest.content().retain();
                fullHttpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, fullHttpRequest.content().readableBytes());
            }
            ChannelPipeline pipeline = clientChannel.pipeline();
            pipeline.remove(HandlerNames.DECOMPRESS);
            pipeline.remove(HandlerNames.AGGREGATOR);
            preHandle(clientChannel, fullHttpRequest);
        } else if (match(httpRequest)) {
            clientChannel.pipeline()
                .addAfter(HandlerNames.DECODER, HandlerNames.DECOMPRESS, new HttpContentDecompressor())
                .addAfter(HandlerNames.DECOMPRESS, HandlerNames.AGGREGATOR, new HttpObjectAggregator(maxContentLength))
                .fireChannelRead(httpRequest);
        }
    }

    public abstract void preHandle(Channel clientChannel, FullHttpRequest httpRequest) throws Exception;

    @Override
    public final void preHandle(Channel clientChannel, HttpContent httpContent) throws Exception {

    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public FullHttpRequestInterceptor setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return this;
    }
}
