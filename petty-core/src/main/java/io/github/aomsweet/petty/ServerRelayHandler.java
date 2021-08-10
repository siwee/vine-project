package io.github.aomsweet.petty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class ServerRelayHandler extends RelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(ServerRelayHandler.class);

    public ServerRelayHandler(PettyServer petty, Channel relayChannel) {
        super(petty, logger);
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        relay(ctx, msg);
    }
}
