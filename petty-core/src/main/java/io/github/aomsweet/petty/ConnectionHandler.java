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
public abstract class ConnectionHandler extends ChannelInboundHandlerAdapter {

    protected final InternalLogger logger;
    protected final PettyServer petty;

    protected Status status;
    protected Channel relayChannel;

    public ConnectionHandler(PettyServer petty, InternalLogger logger) {
        this.petty = petty;
        this.logger = logger;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (relayChannel != null && relayChannel.isActive()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} INACTIVE. CLOSING RELAY CHANNEL {}", ctx.channel(), relayChannel);
            }
            ChannelUtils.closeOnFlush(relayChannel);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (status == Status.CONNECTED) {
            if (relayChannel.isActive()) {
                relayChannel.writeAndFlush(msg);
            } else {
                ReferenceCountUtil.release(msg);
                release(ctx);
            }
        } else {
            channelRead0(ctx, msg);
        }
    }

    public abstract void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception;

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
            ctx.close();
        }
    }

    public enum Status {

        UNCONNECTED, CONNECTED

    }

}
