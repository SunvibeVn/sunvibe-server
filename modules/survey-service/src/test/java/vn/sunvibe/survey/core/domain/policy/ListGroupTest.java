package vn.sunvibe.survey.core.domain.policy;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The groups of the survey list (survey-schedule spec A-11), by Vietnam days. */
class ListGroupTest {

    /** 2026-10-24 00:30 Vietnam time. */
    private static final Instant NOW = Instant.parse("2026-10-23T17:30:00Z");

    @Test
    void of_endedAppointment_isOverdue() {
        assertEquals(ListGroup.OVERDUE, ListGroup.of(Instant.parse("2026-10-23T16:00:00Z"), 90, NOW));
        assertEquals(ListGroup.OVERDUE, ListGroup.of(Instant.parse("2026-10-23T16:00:00Z"), 60, NOW));
    }

    /** Review Focus 4: started yesterday 23:00 for 480 minutes, still running at 00:30. */
    @Test
    void of_runningSinceYesterday_isToday() {
        assertEquals(ListGroup.TODAY, ListGroup.of(Instant.parse("2026-10-23T16:00:00Z"), 480, NOW));
    }

    @Test
    void of_laterToday_isToday_andFromTomorrowMidnight_isUpcoming() {
        assertEquals(ListGroup.TODAY, ListGroup.of(Instant.parse("2026-10-24T16:59:00Z"), 15, NOW));
        assertEquals(ListGroup.UPCOMING, ListGroup.of(Instant.parse("2026-10-24T17:00:00Z"), 15, NOW));
    }
}
