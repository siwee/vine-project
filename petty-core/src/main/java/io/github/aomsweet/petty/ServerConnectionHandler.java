package io.github.aomsweet.petty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class ServerConnectionHandler extends ConnectionHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(ServerConnectionHandler.class);

    public ServerConnectionHandler(PettyServer petty, InternalLogger logger) {
        super(petty, logger);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(msg);
    }
}
