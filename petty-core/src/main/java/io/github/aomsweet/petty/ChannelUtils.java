package io.github.aomsweet.petty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;

/**
 * @author aomsweet
 */
public class ChannelUtils {

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel ch) {
        closeOnFlush(ch, Unpooled.EMPTY_BUFFER);
    }

    public static void closeOnFlush(Channel ch, Object msg) {
        if (ch.isActive()) {
            ch.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
