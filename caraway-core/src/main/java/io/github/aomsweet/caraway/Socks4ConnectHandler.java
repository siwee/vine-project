package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class Socks4ConnectHandler extends ConnectHandler<Socks4CommandRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(Socks4ConnectHandler.class);

    public static final DefaultSocks4CommandResponse SUCCESS_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS);
    public static final DefaultSocks4CommandResponse REJECTED_OR_FAILED_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);

    public Socks4ConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Socks4CommandRequest) {
            doConnectServer(ctx, ctx.channel(), (Socks4CommandRequest) msg);
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    @Override
    void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, Socks4CommandRequest request) {
        clientChannel.writeAndFlush(SUCCESS_RESPONSE).addListener(future -> {
            if (future.isSuccess()) {
                clientChannel.pipeline().remove(Socks4ConnectHandler.this);
                relayDucking(clientChannel, serverChannel);
            } else {
                release(clientChannel, serverChannel);
            }
        });
    }

    @Override
    void failConnect(ChannelHandlerContext ctx, Channel clientChannel, Socks4CommandRequest request) {
        ChannelUtils.closeOnFlush(clientChannel, REJECTED_OR_FAILED_RESPONSE);
    }

    @Override
    InetSocketAddress getServerAddress(Socks4CommandRequest request) {
        return InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
    }
}
