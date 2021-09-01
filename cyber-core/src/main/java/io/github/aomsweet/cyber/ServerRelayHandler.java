package io.github.aomsweet.cyber;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class ServerRelayHandler extends RelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(ServerRelayHandler.class);

    public ServerRelayHandler(CyberServer cyber, Channel relayChannel) {
        super(cyber, logger);
        this.relayChannel = relayChannel;
        this.state = State.READY;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        relay(ctx, msg);
    }
}
