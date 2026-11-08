package vn.sunvibe.survey.core.domain.policy.media;

import java.util.Optional;

/**
 * Limits of survey photos (survey-media spec 4.2, A-3, A-27): counted under the per-user advisory lock and the set
 * lock by the use cases, which pass the counts before the new photo.
 */
public final class MediaLimits {

    /** BR-6: at most 100 selected photos per checklist. */
    public static final int MAX_SELECTED = 100;
    /** survey-media spec A-3: at most 20 pending photos per checklist. */
    public static final int MAX_PENDING = 20;
    /** survey-media spec A-3: at most 300 photo records per user per rolling hour. */
    public static final int MAX_RECORDS_PER_HOUR = 300;
    /** survey-media spec A-3: at most 3 GiB of declared size per user per rolling hour. */
    public static final long MAX_BYTES_PER_HOUR = 3L * 1024 * 1024 * 1024;
    /** survey-media spec A-27: at most 20 expensive outcomes per uploader per hour. */
    public static final int MAX_EXPENSIVE_PER_HOUR = 20;

    private MediaLimits() {
    }

    /** survey-media spec 5.4 create (13): the field of the reached limit, {@code selected} first, then {@code pending}. */
    public static Optional<String> selectionBreach(int selectedCount, int pendingCount) {
        if (selectedCount >= MAX_SELECTED) {
            return Optional.of("selected");
        }
        if (pendingCount >= MAX_PENDING) {
            return Optional.of("pending");
        }
        return Optional.empty();
    }

    /** survey-media spec A-3, 5.4 create (11): {@code records} or {@code bytes} when the new photo would pass the hourly limit. */
    public static Optional<String> hourlyBreach(int recordsInHour, long bytesInHour, long newBytes) {
        if (recordsInHour >= MAX_RECORDS_PER_HOUR) {
            return Optional.of("records");
        }
        if (bytesInHour + newBytes > MAX_BYTES_PER_HOUR) {
            return Optional.of("bytes");
        }
        return Optional.empty();
    }

    /** survey-media spec A-3: true when the user has all content bodies in use (3 by default, survey-media spec 9 setting). */
    public static boolean bodiesFull(int bodiesInUse, int bodiesPerUser) {
        return bodiesInUse >= bodiesPerUser;
    }

    /** survey-media spec A-27: true when the uploader reached the expensive outcomes of the hour. */
    public static boolean expensiveReached(int expensiveInHour) {
        return expensiveInHour >= MAX_EXPENSIVE_PER_HOUR;
    }
}
