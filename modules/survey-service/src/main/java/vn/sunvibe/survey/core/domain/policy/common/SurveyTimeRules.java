package vn.sunvibe.survey.core.domain.policy.common;

import vn.sunvibe.survey.core.domain.exception.common.InvalidSurveyValueException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Time rules of survey appointments (survey-schedule BR-4, survey-schedule spec 4.8): times are instants in whole minutes; staff enter
 * and read them in Vietnam time. A start lies between the current minute and 90 days ahead; a duration is 15..480
 * minutes in steps of 15; an arrival is not in the future, not before the minute the appointment was created and at
 * most 60 minutes before the start in force at that minute. The caller passes "now"; nothing here reads a clock.
 */
public final class SurveyTimeRules {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int MAX_DAYS_AHEAD = 90;
    public static final Duration EARLY_ARRIVAL = Duration.ofMinutes(60);
    public static final int MIN_DURATION = 15;
    public static final int MAX_DURATION = 480;
    public static final int DURATION_STEP = 15;
    /** survey-schedule spec A-5: the duration of a booking that leaves it out. */
    public static final int DEFAULT_DURATION = 60;

    private static final Instant QUERY_RANGE_FROM = Instant.EPOCH;
    private static final Instant QUERY_RANGE_BEFORE = Instant.parse("+10000-01-01T00:00:00Z");

    private SurveyTimeRules() {
    }

    /** survey-schedule spec A-25: seconds and fractions are zero. */
    public static void requireWholeMinute(String field, Instant instant) {
        if (instant == null) {
            throw new InvalidSurveyValueException(field, field + " is required.");
        }
        if (!instant.equals(minuteOf(instant))) {
            throw new InvalidSurveyValueException(field, field + " must be a whole minute.");
        }
    }

    /** survey-schedule BR-4: not before the current minute, at most 90 days after it. */
    public static void requireStartWindow(Instant start, Instant now) {
        Instant currentMinute = minuteOf(now);
        if (start.isBefore(currentMinute)) {
            throw new InvalidSurveyValueException("startAt", "startAt cannot be in the past.");
        }
        if (start.isAfter(currentMinute.plus(Duration.ofDays(MAX_DAYS_AHEAD)))) {
            throw new InvalidSurveyValueException("startAt",
                    "startAt must be at most " + MAX_DAYS_AHEAD + " days ahead.");
        }
    }

    /**
     * A technical bound for a time that only reads (the overlap pre-check, SV-033): from 1970 to the end of year 9999.
     * Outside it the {@code Instant} arithmetic overflows or PostgreSQL refuses the timestamp, so the request would
     * answer 500. Inside it every time is answered as before; the booking window of {@link #requireStartWindow} is
     * not applied to a read.
     */
    public static void requireQueryRange(String field, Instant instant) {
        if (instant.isBefore(QUERY_RANGE_FROM) || !instant.isBefore(QUERY_RANGE_BEFORE)) {
            throw new InvalidSurveyValueException(field, field + " is out of the supported range.");
        }
    }

    /** survey-schedule BR-4: 15..480 minutes in steps of 15. */
    public static void requireDuration(int minutes) {
        if (minutes < MIN_DURATION || minutes > MAX_DURATION || minutes % DURATION_STEP != 0) {
            throw new InvalidSurveyValueException("durationMinutes", "durationMinutes must be between "
                    + MIN_DURATION + " and " + MAX_DURATION + " in steps of " + DURATION_STEP + ".");
        }
    }

    /** survey-schedule spec 4.8, E-7: an arrival is not in the future and not before the creation minute. */
    public static void requireArrivalWindow(Instant arrivedAt, Instant now, Instant createdAt) {
        if (arrivedAt.isAfter(now)) {
            throw new InvalidSurveyValueException("arrivedAt", "arrivedAt cannot be in the future.");
        }
        if (arrivedAt.isBefore(minuteOf(createdAt))) {
            throw new InvalidSurveyValueException("arrivedAt",
                    "arrivedAt cannot be before the appointment was booked.");
        }
    }

    /** survey-schedule US-3 AC-1: at most 60 minutes before the start in force at the arrival. */
    public static void requireArrivalNotTooEarly(Instant arrivedAt, Instant startInForce) {
        if (arrivedAt.isBefore(startInForce.minus(EARLY_ARRIVAL))) {
            throw new InvalidSurveyValueException("arrivedAt",
                    "arrivedAt cannot be more than 60 minutes before the start.");
        }
    }

    /** The instant cut to its minute. */
    public static Instant minuteOf(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    /** The Vietnam date of an instant. */
    public static LocalDate vietnamDate(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    /** 00:00 Vietnam time of the day after the instant's Vietnam date. */
    public static Instant startOfNextVietnamDay(Instant instant) {
        return vietnamDate(instant).plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}
