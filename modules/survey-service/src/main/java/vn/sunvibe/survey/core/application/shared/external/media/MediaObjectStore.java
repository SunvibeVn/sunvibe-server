package vn.sunvibe.survey.core.application.shared.external.media;

import java.io.InputStream;
import java.util.List;

/**
 * The private object store of survey photos (survey-media spec 4.12, ADR-011 decision 1; adapters: local for dev and
 * test, S3 for Cloudflare R2). Every key is written once with a create-only put (I-3); bytes behind a key never change.
 * Background work (content PUT, worker, sweeper, listing, deletion), content serving and health probes use three
 * separate bounded clients (A-25). Failures are {@link StoreUnavailableException} with a code, never SDK text; keys,
 * buckets and endpoints are never part of a message (spec 4.9).
 */
public interface MediaObjectStore {

    /** The two private buckets (spec 4.12): raw sources in quarantine, processed variants in media. */
    enum Bucket {
        /** Raw sources under {@code quarantine/<mediaId>/<attempt>}; probe keys under {@code quarantine/health/}. */
        QUARANTINE,
        /** Variants under {@code media/<mediaId>/<attempt>/<variant>}; probe keys under {@code health/}. */
        MEDIA
    }

    /** The answer of a create-only put. */
    enum PutOutcome {
        /** The key did not exist and now holds the bytes. */
        CREATED,
        /** The key exists (a 412 to {@code If-None-Match: *}); nothing was written. */
        EXISTS
    }

    /**
     * A create-only put of known length (background client).
     *
     * @throws StoreUnavailableException on a store-side error or timeout
     */
    PutOutcome putCreateOnly(Bucket bucket, String key, InputStream content, long length);

    /**
     * Opens an object for background reading (the worker's source read).
     *
     * @throws StoreObjectNotFoundException when the key does not exist
     * @throws StoreUnavailableException on a store-side error or timeout
     */
    StoredObject get(Bucket bucket, String key);

    /**
     * Opens a variant of the media bucket for serving, on the GET client whose pool is bounded with a 2 s acquisition
     * timeout (spec A-25).
     *
     * @throws StoreObjectNotFoundException when the key does not exist
     * @throws StoreUnavailableException on a store-side error, a timeout or no free GET connection in time
     */
    StoredObject getForServing(String key);

    /** Deletes the key; a missing key is no error (background client). */
    void delete(Bucket bucket, String key);

    /** One page of keys under the prefix, in key order, after {@code startAfterOrNull} (background client). */
    ObjectPage list(Bucket bucket, String prefix, String startAfterOrNull, int maxKeys);

    /**
     * spec A-18: a create-only put, get and delete of a unique key under the bucket's health prefix, on the probe
     * client; answers normally only when all three behaved.
     *
     * @throws StoreUnavailableException when the store failed or answered wrongly
     */
    void probe(Bucket bucket);

    /**
     * An opened object: close it to release its connection. {@code abort} ends the stream at once without reading the
     * rest (spec A-25: an S3 stream drops its connection instead of draining it); by default it closes the stream.
     */
    record StoredObject(InputStream content, long length, Runnable abort) implements AutoCloseable {

        public StoredObject(InputStream content, long length) {
            this(content, length, () -> {
                try {
                    content.close();
                } catch (java.io.IOException e) {
                    // the stream is gone either way
                }
            });
        }

        @Override
        public void close() throws java.io.IOException {
            content.close();
        }
    }

    /** One object of a listing. */
    record ListedObject(String key, java.time.Instant lastModified, long size) {

        /** Keys are private: print nothing of them. */
        @Override
        public String toString() {
            return "ListedObject[" + size + " bytes]";
        }
    }

    /** One page of a listing; {@code truncated} when more keys follow the last one. */
    record ObjectPage(List<ListedObject> objects, boolean truncated) {
    }
}
