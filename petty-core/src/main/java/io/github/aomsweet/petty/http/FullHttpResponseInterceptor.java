package io.github.aomsweet.petty.http;

import io.github.aomsweet.petty.HandlerNames;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;

/**
 * @author aomsweet
 */
public abstract class FullHttpResponseInterceptor implements HttpResponseInterceptor {

    /**
     * default max content length size is 8MB
     */
    private static final int DEFAULT_MAX_CONTENT_LENGTH = 1024 * 1024 * 8;

    private int maxContentLength;

    public FullHttpResponseInterceptor() {
        this(DEFAULT_MAX_CONTENT_LENGTH);
    }

    public FullHttpResponseInterceptor(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    @Override
    public void preHandle(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
        if (httpResponse instanceof FullHttpResponse) {
            preHandle(clientChannel, serverChannel, httpRequest, (FullHttpResponse) httpResponse);
            ChannelPipeline pipeline = serverChannel.pipeline();
            pipeline.remove(HandlerNames.DECOMPRESS);
            pipeline.remove(HandlerNames.AGGREGATOR);
        } else if (match(httpRequest, httpResponse)) {
            serverChannel.pipeline()
                .addAfter(HandlerNames.DECODER, HandlerNames.DECOMPRESS, new HttpContentDecompressor())
                .addAfter(HandlerNames.DECOMPRESS, HandlerNames.AGGREGATOR, new HttpObjectAggregator(maxContentLength));
        }
    }

    public abstract void preHandle(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, FullHttpResponse httpResponse) throws Exception;

    @Override
    public final void preHandle(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, HttpContent httpContent) throws Exception {

    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public FullHttpResponseInterceptor setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return this;
    }
}
