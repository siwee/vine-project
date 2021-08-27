package io.github.aomsweet.petty;

import io.github.aomsweet.petty.http.HttpAuthorizationHandler;
import io.github.aomsweet.petty.socks.Socks4ClientRelayHandler;
import io.github.aomsweet.petty.socks.Socks5ClientRelayHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author aomsweet
 */
@ChannelHandler.Sharable
public class PortUnificationServerHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PortUnificationServerHandler.class);

    PettyServer petty;
    HttpAuthorizationHandler httpAuthorizationHandler;

    public PortUnificationServerHandler(PettyServer petty) {
        this.petty = petty;
        this.httpAuthorizationHandler = new HttpAuthorizationHandler(petty);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf in = (ByteBuf) msg;
            final int readerIndex = in.readerIndex();
            if (in.writerIndex() == readerIndex) {
                return;
            }
            ChannelPipeline pipeline = ctx.pipeline().remove(this);
            final byte version = in.getByte(readerIndex);
            if (version == 4) {
                logKnownVersion(ctx, version);
                pipeline.addLast(HandlerNames.DECODER, new Socks4ServerDecoder());
                pipeline.addLast(HandlerNames.RESPONSE_ENCODER, Socks4ServerEncoder.INSTANCE);
                pipeline.addLast(HandlerNames.RELAY, new Socks4ClientRelayHandler(petty));
            } else if (version == 5) {
                logKnownVersion(ctx, version);
                pipeline.addLast(HandlerNames.DECODER, new Socks5InitialRequestDecoder());
                pipeline.addLast(HandlerNames.RESPONSE_ENCODER, Socks5ServerEncoder.DEFAULT);
                pipeline.addLast(HandlerNames.RELAY, new Socks5ClientRelayHandler(petty));
            } else {
                pipeline.addLast(HandlerNames.DECODER, new HttpRequestDecoder());
                pipeline.addLast(httpAuthorizationHandler);
            }
            pipeline.fireChannelRead(msg);
        } else {
            ctx.close();
            ReferenceCountUtil.release(msg);
        }
    }

    private static void logKnownVersion(ChannelHandlerContext ctx, byte version) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} Protocol version: {}({})", ctx.channel(), SocksVersion.valueOf(version));
        }
    }
}
