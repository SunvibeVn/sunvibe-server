package vn.sunvibe.survey.core.domain.model.media;

import java.time.Instant;

/**
 * One run of the full listing of {@code media/} (survey-media spec 4.4 step 4): its lease, the key it listed up to,
 * how many keys it saw and how many orphans it could not delete. A run that listed to the end finishes with no
 * {@code startAfterKeyOrNull}; an abandoned one keeps it.
 */
public record ListingRun(long id, Instant startedAt, String leaseTokenOrNull, Instant leaseUntilOrNull,
                         String startAfterKeyOrNull, long keysSeen, int undeletedCount, Instant finishedAtOrNull) {

    /** Keys are private: ids and counts only. */
    @Override
    public String toString() {
        return "ListingRun[" + id + ", " + keysSeen + " keys, " + undeletedCount + " undeleted]";
    }
}
