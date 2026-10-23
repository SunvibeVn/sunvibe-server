package vn.sunvibe.survey.core.domain.policy;

import java.time.Duration;
import java.time.Instant;

/** The groups of the survey list (survey-schedule US-4, spec A-11), shown in this order. */
public enum ListGroup {
    /** The appointment has ended (start + duration is now or earlier) and is still scheduled. */
    OVERDUE,
    /** Not ended, and started or starting before tomorrow 00:00 Vietnam time. */
    TODAY,
    /** Starting from tomorrow 00:00 Vietnam time. */
    UPCOMING;

    /** The group of a scheduled appointment at the given moment. */
    public static ListGroup of(Instant start, int durationMinutes, Instant now) {
        if (!start.plus(Duration.ofMinutes(durationMinutes)).isAfter(now)) {
            return OVERDUE;
        }
        return start.isBefore(SurveyTimeRules.startOfNextVietnamDay(now)) ? TODAY : UPCOMING;
    }
}
