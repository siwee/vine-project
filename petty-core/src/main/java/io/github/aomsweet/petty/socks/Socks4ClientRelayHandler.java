package io.github.aomsweet.petty.socks;

import io.github.aomsweet.petty.ChannelUtils;
import io.github.aomsweet.petty.ClientRelayHandler;
import io.github.aomsweet.petty.HandlerNames;
import io.github.aomsweet.petty.PettyServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v4.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

public final class Socks4ClientRelayHandler extends ClientRelayHandler<Socks4CommandRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(Socks4ClientRelayHandler.class);

    public static final DefaultSocks4CommandResponse SUCCESS_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS);
    public static final DefaultSocks4CommandResponse REJECTED_OR_FAILED_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);

    public Socks4ClientRelayHandler(PettyServer petty) {
        super(petty, logger);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Socks4CommandRequest) {
            Socks4CommandRequest request = (Socks4CommandRequest) msg;
            if (request.type() == Socks4CommandType.CONNECT) {
                serverAddress = InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
                doConnectServer(ctx, ctx.channel(), (Socks4CommandRequest) msg);
            } else {
                logger.error("Unsupported Socks4 {} command.", request.type());
                ctx.close();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    protected void onConnected(ChannelHandlerContext ctx, Channel clientChannel, Socks4CommandRequest request) {
        clientChannel.writeAndFlush(SUCCESS_RESPONSE).addListener(future -> {
            if (future.isSuccess()) {
                ChannelPipeline pipeline = clientChannel.pipeline();
                pipeline.remove(HandlerNames.DECODER);
                pipeline.remove(Socks4ServerEncoder.INSTANCE);
                addServerRelayHandler(ctx);
            } else {
                release(ctx);
            }
        });
    }

    @Override
    protected void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, Socks4CommandRequest request) {
        ChannelUtils.closeOnFlush(clientChannel, REJECTED_OR_FAILED_RESPONSE);
    }
}
