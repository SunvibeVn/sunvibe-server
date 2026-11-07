package vn.sunvibe.survey.core.application.shared.media;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.exception.media.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.exception.media.MediaServerBusyException;

/**
 * The read slots of content GETs (survey-media spec A-25): at most N concurrent reads per user and M per instance, in
 * memory (the user bound is per instance, SV-017), taken after the 404 and 409 checks and before the budget and the
 * store call, refused at once (429 {@code concurrency} or 503 MEDIA_SERVER_BUSY), released when the read ends.
 */
@Component
public class MediaReadSlots {

    /** A read slot frees within seconds: a variant streams in well under its deadline. */
    public static final long RETRY_AFTER_SECONDS = 2;

    private final MediaReadSettings settings;
    private final Semaphore instance;
    private final Map<String, Integer> perUser = new ConcurrentHashMap<>();

    public MediaReadSlots(MediaReadSettings settings) {
        this.settings = settings;
        this.instance = new Semaphore(settings.instanceConcurrency());
    }

    /**
     * @throws MediaRateLimitedException 429 on {@code concurrency} when the user has all their reads
     * @throws MediaServerBusyException 503 when the instance has all its reads
     */
    public Lease acquire(String userId) {
        boolean[] taken = {false};
        perUser.compute(userId, (key, count) -> {
            int current = count == null ? 0 : count;
            if (current >= settings.userConcurrency()) {
                return count;
            }
            taken[0] = true;
            return current + 1;
        });
        if (!taken[0]) {
            throw new MediaRateLimitedException("concurrency", RETRY_AFTER_SECONDS);
        }
        if (!instance.tryAcquire()) {
            releaseUser(userId);
            throw new MediaServerBusyException(RETRY_AFTER_SECONDS);
        }
        return new Lease(userId);
    }

    private void releaseUser(String userId) {
        perUser.computeIfPresent(userId, (key, count) -> count <= 1 ? null : count - 1);
    }

    /** Both slots of one read; closing frees them once. */
    public final class Lease implements AutoCloseable {

        private final String userId;
        private boolean closed;

        private Lease(String userId) {
            this.userId = userId;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                instance.release();
                releaseUser(userId);
            }
        }
    }
}
