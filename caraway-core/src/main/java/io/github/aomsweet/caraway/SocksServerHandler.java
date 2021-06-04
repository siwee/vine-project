package io.github.aomsweet.caraway;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.*;

/**
 * @author aomsweet
 */
@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> {

    public static final DefaultSocks5InitialResponse NO_AUTH_RESPONSE = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
    public static final DefaultSocks5InitialResponse PASSWORD_RESPONSE = new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD);

    public static final DefaultSocks5PasswordAuthResponse AUTH_SUCCESS = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS);
    public static final DefaultSocks5PasswordAuthResponse AUTH_FAILURE = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE);

    boolean enableAuthorization = false;

    CarawayServer caraway;
    ProxyAuthenticator proxyAuthenticator;
    Socks4ConnectHandler socks4ConnectHandler;
    Socks5ConnectHandler socks5ConnectHandler;

    public SocksServerHandler(CarawayServer caraway) {
        this.caraway = caraway;
        this.socks4ConnectHandler = new Socks4ConnectHandler(caraway);
        this.socks5ConnectHandler = new Socks5ConnectHandler(caraway);
        this.proxyAuthenticator = caraway.getProxyAuthenticator();
        this.enableAuthorization = this.proxyAuthenticator != null;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) throws Exception {
        ChannelPipeline pipeline = ctx.pipeline();
        switch (socksRequest.version()) {
            case SOCKS4a:
                Socks4CommandRequest socksV4CmdRequest = (Socks4CommandRequest) socksRequest;
                if (socksV4CmdRequest.type() == Socks4CommandType.CONNECT) {
                    pipeline.addLast(socks4ConnectHandler);
                    pipeline.remove(this);
                    ctx.fireChannelRead(socksRequest);
                } else {
                    ctx.close();
                }
                break;
            case SOCKS5:
                if (socksRequest instanceof Socks5InitialRequest) {
                    pipeline.remove(Socks5InitialRequestDecoder.class);
                    if (enableAuthorization ||
                        ((Socks5InitialRequest) socksRequest).authMethods().contains(Socks5AuthMethod.PASSWORD)) {
                        pipeline.addFirst(new Socks5PasswordAuthRequestDecoder());
                        ctx.writeAndFlush(PASSWORD_RESPONSE);
                    } else {
                        pipeline.addFirst(new Socks5CommandRequestDecoder());
                        ctx.writeAndFlush(NO_AUTH_RESPONSE);
                    }
                } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
                    pipeline.remove(Socks5PasswordAuthRequestDecoder.class);
                    pipeline.addFirst(new Socks5CommandRequestDecoder());
                    DefaultSocks5PasswordAuthResponse authResponse = !enableAuthorization
                        || proxyAuthenticator.authenticate((Socks5PasswordAuthRequest) socksRequest)
                        ? AUTH_SUCCESS : AUTH_FAILURE;
                    ctx.writeAndFlush(authResponse);

                } else if (socksRequest instanceof Socks5CommandRequest) {
                    pipeline.remove(Socks5CommandRequestDecoder.class);

                    Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) socksRequest;
                    if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                        pipeline.addLast(socks5ConnectHandler);
                        pipeline.remove(this);
                        System.err.println(pipeline);
                        ctx.fireChannelRead(socksRequest);
                    } else {
                        ctx.close();
                    }
                } else {
                    ctx.close();
                }
                break;
            case UNKNOWN:
                ctx.close();
                break;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        ctx.close();
        throwable.printStackTrace();
    }
}
