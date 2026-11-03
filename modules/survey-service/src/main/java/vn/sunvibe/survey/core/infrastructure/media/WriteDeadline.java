package vn.sunvibe.survey.core.infrastructure.media;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A deadline on sending a request to clamd or the processor (survey-media SV-069, with security re-review I-1): a
 * socket read has a timeout but a write does not, so a peer that stops reading would hang the worker thread. When the
 * request is not sent in time, the connection is closed, which fails the blocked write.
 */
public final class WriteDeadline implements AutoCloseable {

    private static final ScheduledThreadPoolExecutor TIMER = timer();

    private final ScheduledFuture<?> closing;

    private WriteDeadline(Closeable connection, Duration timeout) {
        this.closing = TIMER.schedule(() -> {
            try {
                connection.close();
            } catch (IOException ignored) {
                // already closed
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** One daemon thread; a cancelled deadline leaves the queue at once, with the connection it holds. */
    private static ScheduledThreadPoolExecutor timer() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "media-write-deadline");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    /** Closes {@code connection} unless this deadline is closed within {@code timeout}. */
    public static WriteDeadline arm(Closeable connection, Duration timeout) {
        return new WriteDeadline(connection, timeout);
    }

    /** The request was sent in time: the connection stays open. */
    @Override
    public void close() {
        closing.cancel(false);
    }
}
