package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class Socks4ConnectHandler extends ConnectHandler {

    public static final DefaultSocks4CommandResponse SUCCESS_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS);
    public static final DefaultSocks4CommandResponse REJECTED_OR_FAILED_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);

    public Socks4ConnectHandler(CarawayServer caraway) {
        super(caraway);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Socks4CommandRequest) {
            Socks4CommandRequest request = (Socks4CommandRequest) msg;
            final Channel inboundChannel = ctx.channel();
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener((FutureListener<Channel>) connect -> {
                final Channel outboundChannel = connect.getNow();
                if (connect.isSuccess()) {
                    inboundChannel.writeAndFlush(SUCCESS_RESPONSE).addListener(future -> {
                        ctx.pipeline().remove(Socks4ConnectHandler.this);
                        outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
                        ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                    });
                } else {
                    ChannelUtils.closeOnFlush(inboundChannel, REJECTED_OR_FAILED_RESPONSE);
                }
            });
            InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
            connector.channel(remoteAddress, inboundChannel.eventLoop(), promise).addListener(future -> {
                if (!future.isSuccess()) {
                    ChannelUtils.closeOnFlush(inboundChannel, REJECTED_OR_FAILED_RESPONSE);
                }
            });
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }
}
