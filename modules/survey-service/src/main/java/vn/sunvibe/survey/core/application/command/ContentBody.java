package vn.sunvibe.survey.core.application.command;

import java.io.InputStream;

/**
 * The raw body of a content upload (survey-media spec 4.4 step 2, A-13). {@link #touched()} tells whether anything
 * was read or drained: an answer given before that closes the connection, every later one comes after the drain.
 */
public final class ContentBody {

    private final InputStream stream;
    private boolean touched;

    public ContentBody(InputStream stream) {
        this.stream = stream;
    }

    /** The stream; from now on the body counts as read. */
    public InputStream open() {
        touched = true;
        return stream;
    }

    public boolean touched() {
        return touched;
    }
}
