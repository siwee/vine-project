package io.github.aomsweet.petty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class ServerRelayHandler extends RelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(ServerRelayHandler.class);

    public ServerRelayHandler(Channel relayChannel) {
        super(relayChannel, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
            if (!event.isSuccess()) {
                event.cause().printStackTrace();
            }
        }
        System.err.println(evt);
    }
}
