package io.github.aomsweet.caraway;

import io.github.aomsweet.caraway.http.HttpServerHandler;
import io.github.aomsweet.caraway.socks.Socks4ConnectHandler;
import io.github.aomsweet.caraway.socks.Socks5ConnectHandler;
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

    CarawayServer caraway;
    HttpServerHandler httpServerHandler;

    Socks4ConnectHandler socks4ConnectHandler;
    Socks5ConnectHandler socks5ConnectHandler;

    public PortUnificationServerHandler(CarawayServer caraway) {
        this.caraway = caraway;
        this.httpServerHandler = new HttpServerHandler(caraway);
        this.socks5ConnectHandler = new Socks5ConnectHandler(caraway);

        this.socks4ConnectHandler = new Socks4ConnectHandler(caraway);
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
                pipeline.addLast(socks5ConnectHandler);
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
