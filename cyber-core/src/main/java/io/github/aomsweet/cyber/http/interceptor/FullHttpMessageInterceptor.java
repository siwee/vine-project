package io.github.aomsweet.cyber.http.interceptor;

/**
 * @author aomsweet
 */
public abstract class FullHttpMessageInterceptor<T extends FullHttpMessageInterceptor<T>> {

    protected static final int DEFAULT_MAX_CONTENT_LENGTH = 1024 * 1024 * 8;

    protected int maxContentLength;

    public FullHttpMessageInterceptor() {
        this(DEFAULT_MAX_CONTENT_LENGTH);
    }

    public FullHttpMessageInterceptor(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    @SuppressWarnings("unchecked")
    public T setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return (T) this;
    }
}
