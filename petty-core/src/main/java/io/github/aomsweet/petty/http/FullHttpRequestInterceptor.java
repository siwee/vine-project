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

    public abstract boolean match(HttpRequest httpRequest);

    @Override
    public final void beforeSend(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest) throws Exception {
        if (httpRequest instanceof FullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) httpRequest;
            if (fullHttpRequest.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                //TODO Why?
                fullHttpRequest.content().markReaderIndex();
                fullHttpRequest.content().retain();
                fullHttpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, fullHttpRequest.content().readableBytes());
            }
            beforeSend(clientChannel, serverChannel, fullHttpRequest);
        } else if (match(httpRequest)) {
            ChannelPipeline pipeline = clientChannel.pipeline();
            pipeline.addAfter(HandlerNames.DECODER, HandlerNames.DECOMPRESS, new HttpContentDecompressor());
            pipeline.addAfter(HandlerNames.DECOMPRESS, HandlerNames.AGGREGATOR, new HttpObjectAggregator(maxContentLength));
            pipeline.fireChannelRead(httpRequest);
        }
    }

    public abstract void beforeSend(Channel clientChannel, Channel serverChannel, FullHttpRequest fullHttpRequest) throws Exception;

    @Override
    public final void beforeSend(Channel clientChannel, Channel serverChannel, HttpContent httpContent) throws Exception {

    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public FullHttpRequestInterceptor setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return this;
    }
}
