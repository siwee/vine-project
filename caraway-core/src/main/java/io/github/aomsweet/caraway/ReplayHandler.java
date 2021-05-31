package io.github.aomsweet.caraway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
public class ReplayHandler extends ChannelInboundHandlerAdapter {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(ReplayHandler.class);

    Channel replayChannel;

    public ReplayHandler(Channel replayChannel) {
        this.replayChannel = replayChannel;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (replayChannel.isActive()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} INACTIVE. CLOSING REPLAY CHANNEL {}", ctx.channel(), replayChannel);
            }
            ByteBuf emptyBuf = replayChannel.alloc().buffer(0, 0);
            replayChannel.writeAndFlush(emptyBuf).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (replayChannel.isActive()) {
            replayChannel.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
            replayChannel.close();
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
        logger.info("{} WRITABILITY CHANGED. CURRENT STATUS: {}. {} {}", ctx.channel(),
            isWritable ? "WRITABLE" : "NOT WRITABLE", replayChannel,
            isWritable ? "ENABLE AUTO READ" : "DISABLE AUTO READ");
        replayChannel.config().setAutoRead(isWritable);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
        logger.error(cause.getMessage(), cause);
    }

}
