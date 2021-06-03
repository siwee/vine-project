package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class Socks5ConnectHandler extends ConnectHandler<Socks5CommandRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(Socks5ConnectHandler.class);

    public Socks5ConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Socks5CommandRequest) {
            doConnectServer(ctx, ctx.channel(), (Socks5CommandRequest) msg);
        } else {
            ctx.close();
        }
    }

    @Override
    void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, Socks5CommandRequest request) {
        Object response = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
            request.dstAddrType(), request.dstAddr(), request.dstPort());
        clientChannel.writeAndFlush(response).addListener(future -> {
            if (future.isSuccess()) {
                ctx.pipeline().remove(Socks5ConnectHandler.this);
                relayDucking(clientChannel, serverChannel);
            } else {
                release(clientChannel, serverChannel);
            }
        });
    }

    @Override
    void failConnect(ChannelHandlerContext ctx, Channel clientChannel, Socks5CommandRequest request) {
        Object response = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType());
        ChannelUtils.closeOnFlush(clientChannel, response);
    }

    @Override
    InetSocketAddress getServerAddress(Socks5CommandRequest request) {
        return InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
    }
}
