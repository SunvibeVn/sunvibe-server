package vn.sunvibe.survey.core.application.shared.external.media;

import java.io.InputStream;
import java.time.Instant;

/**
 * The private spool of raw photo bodies (survey-media spec 4.4 step 2, A-13; ADR-011 decision 3): a body is counted and
 * hashed into an owner-only file with a random name while no transaction or connection is open, and that file is
 * deleted when the body is closed or refused. The idle read timeout is the container's; the total deadline is checked
 * between reads.
 */
public interface MediaBodySpool {

    /**
     * Reads exactly {@code length} bytes.
     *
     * @throws vn.sunvibe.survey.core.application.exception.media.MediaBodyIncompleteException when the body ends early, a read
     *         fails or the deadline passes; nothing is left behind
     */
    SpooledBody receive(InputStream body, long length, Instant deadline);

    /** A received body; closing it deletes its file. */
    interface SpooledBody extends AutoCloseable {

        /** The lower-case hex SHA-256 of the bytes. */
        String sha256Hex();

        long length();

        /** The bytes, for the one create-only write to the store. */
        InputStream open();

        @Override
        void close();
    }
}
