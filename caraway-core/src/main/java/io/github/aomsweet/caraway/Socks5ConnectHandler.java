package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class Socks5ConnectHandler extends ConnectHandler {

    public Socks5ConnectHandler(CarawayServer caraway) {
        super(caraway);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Socks5CommandRequest) {
            socks5Handle(ctx, (Socks5CommandRequest) msg);
        } else {
            ctx.close();
        }
    }

    public void socks5Handle(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        final Channel inboundChannel = ctx.channel();
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener((FutureListener<Channel>) future -> {
            final Channel outboundChannel = future.getNow();
            if (future.isSuccess()) {
                inboundChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                    request.dstAddrType(),
                    request.dstAddr(),
                    request.dstPort())).addListener((ChannelFutureListener) channelFuture -> {
                    ctx.pipeline().remove(Socks5ConnectHandler.this);
                    outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
                    ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                });
            } else {
                Object response = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType());
                ChannelUtils.closeOnFlush(inboundChannel, response);
            }
        });

        InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
        connector.channel(remoteAddress, inboundChannel.eventLoop(), promise).addListener(future -> {
            if (!future.isSuccess()) {
                Object response = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType());
                ChannelUtils.closeOnFlush(inboundChannel, response);
            }
        });
    }
}
