package io.github.aomsweet.caraway.socks;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.ChannelUtils;
import io.github.aomsweet.caraway.ConnectHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v4.*;
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
            Socks4CommandRequest request = (Socks4CommandRequest) msg;
            if (request.type() == Socks4CommandType.CONNECT) {
                doConnectServer(ctx, ctx.channel(), (Socks4CommandRequest) msg);
            } else {
                logger.error("Unsupported Socks4 {} command.", request.type());
                ctx.close();
            }
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    @Override
    protected void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, Socks4CommandRequest request) {
        clientChannel.writeAndFlush(SUCCESS_RESPONSE).addListener(future -> {
            if (future.isSuccess()) {
                clientChannel.pipeline().remove(Socks4ServerDecoder.class);
                clientChannel.pipeline().remove(Socks4ServerEncoder.INSTANCE);
                clientChannel.pipeline().remove(this);
                relayDucking(clientChannel, serverChannel);
            } else {
                release(clientChannel, serverChannel);
            }
        });
    }

    @Override
    protected void failConnect(ChannelHandlerContext ctx, Channel clientChannel, Socks4CommandRequest request) {
        ChannelUtils.closeOnFlush(clientChannel, REJECTED_OR_FAILED_RESPONSE);
    }

    @Override
    protected InetSocketAddress getServerAddress(Socks4CommandRequest request) {
        return InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
    }
}
