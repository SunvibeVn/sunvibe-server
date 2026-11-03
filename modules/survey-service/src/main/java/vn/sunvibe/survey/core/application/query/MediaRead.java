package vn.sunvibe.survey.core.application.query;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

/**
 * An opened content read (survey-media spec A-25, ADR-011 decision 2): the variant's bytes from the store, its length,
 * the read bounds and what closing it releases (the store stream and the read slots). The bytes are never printed.
 */
public final class MediaRead implements AutoCloseable {

    private final String mediaId;
    private final InputStream content;
    private final long length;
    private final boolean download;
    private final Duration noProgressTimeout;
    private final Instant deadline;
    private final AutoCloseable slots;
    private final Runnable abort;
    private boolean closed;
    private boolean aborted;

    public MediaRead(String mediaId, InputStream content, long length, boolean download, Duration noProgressTimeout,
                     Instant deadline, AutoCloseable slots, Runnable abort) {
        this.mediaId = mediaId;
        this.content = content;
        this.length = length;
        this.download = download;
        this.noProgressTimeout = noProgressTimeout;
        this.deadline = deadline;
        this.slots = slots;
        this.abort = abort;
    }

    public String mediaId() {
        return mediaId;
    }

    public InputStream content() {
        return content;
    }

    public long length() {
        return length;
    }

    public boolean download() {
        return download;
    }

    public Duration noProgressTimeout() {
        return noProgressTimeout;
    }

    public Instant deadline() {
        return deadline;
    }

    /**
     * spec A-25: the watchdog ends a stalled or late read's store stream at once (its connection dropped, not drained);
     * the read's slots stay taken until the request thread closes the read, so a thread still blocked writing to the
     * client keeps counting against the caps.
     */
    public synchronized void abortContent() {
        if (closed || aborted) {
            return;
        }
        aborted = true;
        abort.run();
    }

    /** Closes the store stream (releasing its connection), then the read slots; safe to call more than once. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            content.close();
        } catch (IOException e) {
            // the stream is gone either way
        }
        try {
            slots.close();
        } catch (Exception e) {
            throw new IllegalStateException("The read slots could not be released.", e);
        }
    }

    @Override
    public String toString() {
        return "MediaRead[" + mediaId + " " + length + " bytes]";
    }
}
