package io.github.aomsweet.petty;

import io.netty.channel.Channel;
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

}
