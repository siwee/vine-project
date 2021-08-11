package io.github.aomsweet.petty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author aomsweet
 */
public abstract class RelayHandler extends ChannelInboundHandlerAdapter {

    protected final InternalLogger logger;
    protected final PettyServer petty;

    protected Channel relayChannel;

    public RelayHandler(PettyServer petty, InternalLogger logger) {
        this.petty = petty;
        this.logger = logger;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (relayChannel != null && relayChannel.isActive()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} INACTIVE. CLOSING RELAY CHANNEL {}", ctx.channel(), relayChannel);
            }
            release(ctx);
        }
    }

    public void relay(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg);
        } else {
            release(ctx);
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean isWritable = ctx.channel().isWritable();
        if (logger.isDebugEnabled()) {
            logger.debug("{} WRITABILITY CHANGED. CURRENT STATUS: {}. {} {}", ctx.channel(),
                isWritable ? "WRITABLE" : "NOT WRITABLE", relayChannel,
                isWritable ? "ENABLE AUTO READ" : "DISABLE AUTO READ");
        }
        relayChannel.config().setAutoRead(isWritable);
    }

    public void release(ChannelHandlerContext ctx) {
        ctx.close();
        if (relayChannel != null && relayChannel.isActive()) {
            ChannelUtils.closeOnFlush(relayChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            if (cause instanceof IOException || cause instanceof RejectedExecutionException) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{}: {}", cause.getClass().getName(), cause.getMessage(), cause);
                } else {
                    logger.info("{}: {}", cause.getClass().getName(), cause.getMessage());
                }
            } else {
                logger.error("{}: {}", cause.getClass().getName(), cause.getMessage(), cause);
            }
        } finally {
            release(ctx);
        }
    }

}
