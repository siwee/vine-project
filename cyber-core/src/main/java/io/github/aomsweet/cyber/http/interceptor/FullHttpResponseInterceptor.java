package io.github.aomsweet.cyber.http.interceptor;

import io.github.aomsweet.cyber.HandlerNames;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;

/**
 * @author aomsweet
 */
public abstract class FullHttpResponseInterceptor extends FullHttpMessageInterceptor<FullHttpResponseInterceptor>
    implements HttpResponseInterceptor {

    public FullHttpResponseInterceptor() {
        super();
    }

    public FullHttpResponseInterceptor(int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    public boolean preHandle(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
        ChannelPipeline pipeline = serverChannel.pipeline();
        if (httpResponse instanceof FullHttpResponse) {
            pipeline.remove(HandlerNames.DECOMPRESS);
            pipeline.remove(HandlerNames.AGGREGATOR);
            return preHandle(clientChannel, serverChannel, httpRequest, (FullHttpResponse) httpResponse);
        } else {
            pipeline
                .addAfter(HandlerNames.DECODER, HandlerNames.DECOMPRESS, new HttpContentDecompressor())
                .addAfter(HandlerNames.DECOMPRESS, HandlerNames.AGGREGATOR, new HttpObjectAggregator(maxContentLength))
                .fireChannelRead(httpResponse);
            return false;
        }
    }

    public abstract boolean preHandle(Channel clientChannel, Channel serverChannel, HttpRequest httpRequest, FullHttpResponse httpResponse) throws Exception;
}
