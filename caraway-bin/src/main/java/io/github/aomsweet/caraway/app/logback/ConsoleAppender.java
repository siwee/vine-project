package io.github.aomsweet.caraway.app.logback;

import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.joran.spi.ConsoleTarget;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.WarnStatus;
import org.fusesource.jansi.AnsiConsole;
import org.fusesource.jansi.AnsiPrintStream;

import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author aomsweet
 */
public class ConsoleAppender<E> extends OutputStreamAppender<E> {

    protected ConsoleTarget target = ConsoleTarget.SystemOut;
    protected boolean withJansi = false;

    /**
     * Sets the value of the <b>Target</b> option. Recognized values are
     * "System.out" and "System.err". Any other value will be ignored.
     */
    public void setTarget(String value) {
        ConsoleTarget t = ConsoleTarget.findByName(value.trim());
        if (t == null) {
            targetWarn(value);
        } else {
            target = t;
        }
    }

    /**
     * Returns the current value of the <b>target</b> property. The default value
     * of the option is "System.out".
     * <p>
     * See also {@link #setTarget}.
     */
    public String getTarget() {
        return target.getName();
    }

    private void targetWarn(String val) {
        Status status = new WarnStatus("[" + val + "] should be one of " + Arrays.toString(ConsoleTarget.values()), this);
        status.add(new WarnStatus("Using previously set target, System.out by default.", this));
        addStatus(status);
    }

    @Override
    public void start() {
        OutputStream targetStream = target.getStream();
        // enable jansi only if withJansi set to true
        if (withJansi) {
            targetStream = wrapWithJansi(targetStream);
        }
        setOutputStream(targetStream);
        super.start();
    }

    private OutputStream wrapWithJansi(OutputStream targetStream) {
        try {
            addInfo("Enabling JANSI AnsiPrintStream for the console.");
            AnsiPrintStream ansiPrintStream = AnsiConsole.out();
            ansiPrintStream.install();
            return ansiPrintStream;
        } catch (Exception e) {
            addWarn("Failed to create AnsiPrintStream. Falling back on the default stream.", e);
        }
        return targetStream;
    }

    /**
     * @return whether to use JANSI or not.
     */
    public boolean isWithJansi() {
        return withJansi;
    }

    /**
     * If true, this appender will output to a stream provided by the JANSI
     * library.
     *
     * @param withJansi whether to use JANSI or not.
     * @since 1.0.5
     */
    public void setWithJansi(boolean withJansi) {
        this.withJansi = withJansi;
    }

}
