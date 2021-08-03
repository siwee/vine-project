package io.github.aomsweet.petty;

import io.github.aomsweet.petty.http.HttpServerHandler;
import io.github.aomsweet.petty.socks.Socks4ConnectHandler;
import io.github.aomsweet.petty.socks.Socks5ConnectHandler;
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
    HttpServerHandler httpServerHandler;

    Socks4ConnectHandler socks4ConnectHandler;
    Socks5ConnectHandler socks5ConnectHandler;

    public PortUnificationServerHandler(PettyServer petty) {
        this.petty = petty;
        this.httpServerHandler = new HttpServerHandler(petty);
        this.socks5ConnectHandler = new Socks5ConnectHandler(petty);

        this.socks4ConnectHandler = new Socks4ConnectHandler(petty);
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
                pipeline.addLast(new Socks4ServerDecoder());
                pipeline.addLast(Socks4ServerEncoder.INSTANCE);
                pipeline.addLast(socks4ConnectHandler);
            } else if (version == 5) {
                logKnownVersion(ctx, version);
                pipeline.addLast(new Socks5InitialRequestDecoder());
                pipeline.addLast(Socks5ServerEncoder.DEFAULT);
                if (petty.getUpstreamProxyManager() == null) {
                    pipeline.addLast(socks5ConnectHandler);
                } else {
                    pipeline.addLast(new Socks5ConnectHandler(petty, true));
                }
            } else {
                pipeline.addLast(new HttpRequestDecoder());
                pipeline.addLast(httpServerHandler);
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
