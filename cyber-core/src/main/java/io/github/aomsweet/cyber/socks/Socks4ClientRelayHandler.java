package io.github.aomsweet.cyber.socks;

import io.github.aomsweet.cyber.ClientRelayHandler;
import io.github.aomsweet.cyber.HandlerNames;
import io.github.aomsweet.cyber.CyberServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v4.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author aomsweet
 */
public final class Socks4ClientRelayHandler extends ClientRelayHandler<Socks4CommandRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(Socks4ClientRelayHandler.class);

    public static final DefaultSocks4CommandResponse SUCCESS_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS);
    public static final DefaultSocks4CommandResponse REJECTED_OR_FAILED_RESPONSE = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);

    public Socks4ClientRelayHandler(CyberServer cyber) {
        super(cyber, logger);
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
                release(ctx);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    protected void onConnected(ChannelHandlerContext ctx, Channel clientChannel, Socks4CommandRequest request) {
        clientChannel.writeAndFlush(SUCCESS_RESPONSE);
        ChannelPipeline pipeline = clientChannel.pipeline();
        pipeline.remove(HandlerNames.DECODER);
        pipeline.remove(Socks4ServerEncoder.INSTANCE);
        doServerRelay(ctx);
    }

    @Override
    protected void onConnectFailed(ChannelHandlerContext ctx, Channel clientChannel, Socks4CommandRequest request) {
        ctx.writeAndFlush(REJECTED_OR_FAILED_RESPONSE).addListener(future -> release(ctx));
    }
}
