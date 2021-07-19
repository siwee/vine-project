package io.github.aomsweet.caraway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * @author aomsweet
 */
public class CompleteChannelPromise extends DefaultPromise<Void> implements ChannelPromise {

    Channel channel;

    public CompleteChannelPromise(EventExecutor executor) {
        super(executor);
    }

    public CompleteChannelPromise setChannel(Channel channel) {
        this.channel = channel;
        return this;
    }

    @Override
    protected EventExecutor executor() {
        EventExecutor e = super.executor();
        if (e == null) {
            return channel().eventLoop();
        } else {
            return e;
        }
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public CompleteChannelPromise setSuccess() {
        return setSuccess(null);
    }

    @Override
    public CompleteChannelPromise setSuccess(Void result) {
        super.setSuccess(result);
        return this;
    }

    @Override
    public boolean trySuccess() {
        return trySuccess(null);
    }

    @Override
    public CompleteChannelPromise setFailure(Throwable cause) {
        super.setFailure(cause);
        return this;
    }

    @Override
    public CompleteChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public CompleteChannelPromise addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public CompleteChannelPromise removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public CompleteChannelPromise removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public CompleteChannelPromise sync() throws InterruptedException {
        super.sync();
        return this;
    }

    @Override
    public CompleteChannelPromise syncUninterruptibly() {
        super.syncUninterruptibly();
        return this;
    }

    @Override
    public CompleteChannelPromise await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public CompleteChannelPromise awaitUninterruptibly() {
        super.awaitUninterruptibly();
        return this;
    }

    @Override
    protected void checkDeadLock() {
        if (channel().isRegistered()) {
            super.checkDeadLock();
        }
    }

    @Override
    public CompleteChannelPromise unvoid() {
        return this;
    }

    @Override
    public boolean isVoid() {
        return false;
    }
}
