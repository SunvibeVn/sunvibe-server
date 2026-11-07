package vn.sunvibe.survey.core.testsupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import vn.sunvibe.survey.core.application.exception.media.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.shared.external.media.MediaBodySpool;

/**
 * The private spool in memory: reads exactly the declared length, hashing it; a short body is incomplete. Counts the
 * open spooled bodies so a test sees each one deleted; {@link #afterRead} runs once the body is read, before the store
 * write and the commit (the latch between stream and commit of SM-7).
 */
public final class InMemoryBodySpool implements MediaBodySpool {

    public int open;
    public int received;
    public Runnable afterRead = () -> { };

    @Override
    public SpooledBody receive(InputStream body, long length, Instant deadline) {
        byte[] bytes;
        try {
            bytes = body.readNBytes((int) length);
        } catch (IOException e) {
            throw new MediaBodyIncompleteException();
        }
        if (bytes.length != length) {
            throw new MediaBodyIncompleteException();
        }
        received++;
        open++;
        afterRead.run();
        String sha256 = sha256(bytes);
        return new SpooledBody() {
            private boolean closed;

            @Override
            public String sha256Hex() {
                return sha256;
            }

            @Override
            public long length() {
                return bytes.length;
            }

            @Override
            public InputStream open() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    open--;
                }
            }
        };
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
