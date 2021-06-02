package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author aomsweet
 */
public class RelayHandler extends ChannelInboundHandlerAdapter {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(RelayHandler.class);

    Channel replayChannel;

    public RelayHandler(Channel replayChannel) {
        this.replayChannel = replayChannel;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (replayChannel.isActive()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} INACTIVE. CLOSING REPLAY CHANNEL {}", ctx.channel(), replayChannel);
            }
            ChannelUtils.closeOnFlush(replayChannel);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (replayChannel.isActive()) {
            replayChannel.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
            ChannelUtils.closeOnFlush(replayChannel);
            ctx.close();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean isWritable = ctx.channel().isWritable();
        if (logger.isDebugEnabled()) {
            logger.debug("{} WRITABILITY CHANGED. CURRENT STATUS: {}. {} {}", ctx.channel(),
                isWritable ? "WRITABLE" : "NOT WRITABLE", replayChannel,
                isWritable ? "ENABLE AUTO READ" : "DISABLE AUTO READ");
        }
        replayChannel.config().setAutoRead(isWritable);
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

}
