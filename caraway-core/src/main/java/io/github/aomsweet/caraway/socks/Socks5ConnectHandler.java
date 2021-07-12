package io.github.aomsweet.caraway.socks;

import io.github.aomsweet.caraway.CarawayServer;
import io.github.aomsweet.caraway.ChannelUtils;
import io.github.aomsweet.caraway.ConnectHandler;
import io.github.aomsweet.caraway.ProxyAuthenticator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class Socks5ConnectHandler extends ConnectHandler<Socks5CommandRequest> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(Socks5ConnectHandler.class);

    public static final DefaultSocks5InitialResponse NO_AUTH_RESPONSE = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
    public static final DefaultSocks5InitialResponse PASSWORD_RESPONSE = new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD);

    public static final DefaultSocks5PasswordAuthResponse AUTH_SUCCESS = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS);
    public static final DefaultSocks5PasswordAuthResponse AUTH_FAILURE = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE);

    public Socks5ConnectHandler(CarawayServer caraway) {
        super(caraway, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof SocksMessage) {
            if (((SocksMessage) msg).decoderResult().isSuccess()) {
                ChannelPipeline pipeline = ctx.pipeline();
                ProxyAuthenticator proxyAuthenticator = caraway.getProxyAuthenticator();
                if (msg instanceof Socks5InitialRequest) {
                    pipeline.remove(Socks5InitialRequestDecoder.class);
                    if (proxyAuthenticator != null ||
                        ((Socks5InitialRequest) msg).authMethods().contains(Socks5AuthMethod.PASSWORD)) {
                        pipeline.addFirst(new Socks5PasswordAuthRequestDecoder());
                        ctx.writeAndFlush(PASSWORD_RESPONSE);
                    } else {
                        pipeline.addFirst(new Socks5CommandRequestDecoder());
                        ctx.writeAndFlush(NO_AUTH_RESPONSE);
                    }
                } else if (msg instanceof Socks5PasswordAuthRequest) {
                    pipeline.remove(Socks5PasswordAuthRequestDecoder.class);
                    pipeline.addFirst(new Socks5CommandRequestDecoder());
                    DefaultSocks5PasswordAuthResponse authResponse = proxyAuthenticator == null
                        || proxyAuthenticator.authenticate((Socks5PasswordAuthRequest) msg)
                        ? AUTH_SUCCESS : AUTH_FAILURE;
                    ctx.writeAndFlush(authResponse);
                } else if (msg instanceof Socks5CommandRequest) {
                    pipeline.remove(Socks5CommandRequestDecoder.class);
                    Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) msg;
                    if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                        doConnectServer(ctx, ctx.channel(), (Socks5CommandRequest) msg);
                    } else {
                        logger.error("Unsupported Socks5 {} command.", socks5CmdRequest.type());
                        ctx.close();
                    }
                } else {
                    ctx.close();
                }
            } else {
                logger.error("Bad socks5 request: {}. Channel: {}", msg, ctx.channel());
                ctx.close();
            }
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    @Override
    protected void connected(ChannelHandlerContext ctx, Channel clientChannel, Channel serverChannel, Socks5CommandRequest request) {
        Object response = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
            request.dstAddrType(), request.dstAddr(), request.dstPort());
        clientChannel.writeAndFlush(response).addListener(future -> {
            if (future.isSuccess()) {
                ctx.pipeline().remove(Socks5ServerEncoder.class);
                ctx.pipeline().remove(Socks5ConnectHandler.this);
                relayDucking(clientChannel, serverChannel);
            } else {
                release(clientChannel, serverChannel);
            }
        });
    }

    @Override
    protected void failConnect(ChannelHandlerContext ctx, Channel clientChannel, Socks5CommandRequest request) {
        Object response = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType());
        ChannelUtils.closeOnFlush(clientChannel, response);
    }

    @Override
    protected InetSocketAddress getServerAddress(Socks5CommandRequest request) {
        return InetSocketAddress.createUnresolved(request.dstAddr(), request.dstPort());
    }
}
