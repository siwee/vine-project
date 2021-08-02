package io.github.aomsweet.petty.socks;

import io.github.aomsweet.petty.*;
import io.github.aomsweet.petty.auth.Credentials;
import io.netty.channel.*;
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

    boolean alone;
    Credentials credentials;

    public Socks5ConnectHandler(PettyServer petty) {
        this(petty, false);
    }

    public Socks5ConnectHandler(PettyServer petty, boolean alone) {
        super(petty, logger);
        this.alone = alone;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ChannelPipeline pipeline = ctx.pipeline();
        if (msg instanceof Socks5InitialRequest) {
            initialRequestHandler(ctx, (Socks5InitialRequest) msg, pipeline);
        } else if (msg instanceof Socks5PasswordAuthRequest) {
            authRequestHandler(ctx, (Socks5PasswordAuthRequest) msg, pipeline);
        } else if (msg instanceof Socks5CommandRequest) {
            cmdRequestHandler(ctx, (Socks5CommandRequest) msg, pipeline);
        } else {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    protected void initialRequestHandler(ChannelHandlerContext ctx, Socks5InitialRequest initialRequest, ChannelPipeline pipeline) {
        pipeline.remove(Socks5InitialRequestDecoder.class);
        Object response;
        if (petty.getProxyAuthenticator() != null ||
            initialRequest.authMethods().contains(Socks5AuthMethod.PASSWORD)) {
            pipeline.addFirst(new Socks5PasswordAuthRequestDecoder());
            response = PASSWORD_RESPONSE;
        } else {
            pipeline.addFirst(new Socks5CommandRequestDecoder());
            response = NO_AUTH_RESPONSE;
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    protected void authRequestHandler(ChannelHandlerContext ctx, Socks5PasswordAuthRequest authRequest, ChannelPipeline pipeline) {
        String username = authRequest.username();
        String password = authRequest.password();
        if (alone && (petty.getSocks5ChainedProxyManager()) != null) {
            credentials = new Credentials(username, password);
        }
        ProxyAuthenticator proxyAuthenticator = petty.getProxyAuthenticator();
        if (proxyAuthenticator == null || proxyAuthenticator.authenticate(username, password)) {
            pipeline.remove(Socks5PasswordAuthRequestDecoder.class);
            pipeline.addFirst(new Socks5CommandRequestDecoder());
            ctx.writeAndFlush(AUTH_SUCCESS).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } else {
            ctx.writeAndFlush(AUTH_FAILURE).addListener(ChannelFutureListener.CLOSE);
        }
    }

    protected void cmdRequestHandler(ChannelHandlerContext ctx, Socks5CommandRequest socks5CmdRequest, ChannelPipeline pipeline) {
        pipeline.remove(Socks5CommandRequestDecoder.class);
        if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
            doConnectServer(ctx, ctx.channel(), socks5CmdRequest);
        } else {
            logger.error("Unsupported Socks5 {} command.", socks5CmdRequest.type());
            ctx.close();
        }
    }


    @Override
    protected Credentials getCredentials(Socks5CommandRequest request) {
        return credentials;
    }

    @Override
    protected ChainedProxyManager<Socks5CommandRequest> getChainedProxyManager() {
        return alone ? petty.getSocks5ChainedProxyManager() : null;
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
