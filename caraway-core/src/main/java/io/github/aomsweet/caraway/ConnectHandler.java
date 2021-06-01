package io.github.aomsweet.caraway;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public abstract class ConnectHandler extends ChannelInboundHandlerAdapter {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(ConnectHandler.class);

    CarawayServer caraway;
    ProxyConnector connector;

    public ConnectHandler(CarawayServer caraway) {
        this.caraway = caraway;
        this.connector = caraway.getConnector();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
        logger.error(cause.getMessage(), cause);
    }
}
