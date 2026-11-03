package vn.sunvibe.survey.core.domain.policy.media;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The hourly read budget window with a weighted carry-over (survey-media spec A-26): fixed clock hours, and
 * {@code effectiveLimit = budget − floor(prev × (1 − elapsed))}, where {@code prev} is the previous hour's count and
 * {@code elapsed} the part of the current hour gone. A limit of 0 or less refuses without counting.
 */
public final class ReadBudgetArithmetic {

    private static final long HOUR_MILLIS = Duration.ofHours(1).toMillis();

    private ReadBudgetArithmetic() {
    }

    /** The application-clock time truncated to the hour: the row key of the hour. */
    public static Instant hourStart(Instant now) {
        return now.truncatedTo(ChronoUnit.HOURS);
    }

    /** spec A-26: the limit this hour still allows, given the previous hour's count. */
    public static long effectiveLimit(long budget, long previousHourCount, Instant now) {
        long elapsedMillis = Duration.between(hourStart(now), now).toMillis();
        long carried = Math.multiplyExact(previousHourCount, HOUR_MILLIS - elapsedMillis) / HOUR_MILLIS;
        return budget - carried;
    }

    /** spec A-26: {@code effectiveLimit ≤ 0} refuses without counting. */
    public static boolean refusesWithoutCounting(long effectiveLimit) {
        return effectiveLimit <= 0;
    }
}
