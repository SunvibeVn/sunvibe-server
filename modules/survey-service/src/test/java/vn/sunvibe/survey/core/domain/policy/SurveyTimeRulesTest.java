package vn.sunvibe.survey.core.domain.policy;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The time rules of survey-schedule BR-4 and spec 4.8 (W15-3, W15-16). */
class SurveyTimeRulesTest {

    /** 2026-10-23 10:15:20 Vietnam time. */
    private static final Instant NOW = Instant.parse("2026-10-23T03:15:20Z");
    private static final Instant CURRENT_MINUTE = Instant.parse("2026-10-23T03:15:00Z");

    @Test
    void requireStartWindow_fromTheCurrentMinuteTo90DaysAhead() {
        assertDoesNotThrow(() -> SurveyTimeRules.requireStartWindow(CURRENT_MINUTE, NOW));
        assertDoesNotThrow(() -> SurveyTimeRules.requireStartWindow(CURRENT_MINUTE.plus(Duration.ofDays(90)), NOW));
        assertField("startAt", () -> SurveyTimeRules.requireStartWindow(CURRENT_MINUTE.minusSeconds(60), NOW));
        assertField("startAt", () -> SurveyTimeRules.requireStartWindow(
                CURRENT_MINUTE.plus(Duration.ofDays(90)).plusSeconds(60), NOW));
    }

    @Test
    void requireWholeMinute_secondsOrFractions_areRefused() {
        assertDoesNotThrow(() -> SurveyTimeRules.requireWholeMinute("startAt", CURRENT_MINUTE));
        assertField("startAt", () -> SurveyTimeRules.requireWholeMinute("startAt", CURRENT_MINUTE.plusSeconds(30)));
        assertField("arrivedAt", () -> SurveyTimeRules.requireWholeMinute("arrivedAt",
                CURRENT_MINUTE.plusMillis(1)));
        assertField("startAt", () -> SurveyTimeRules.requireWholeMinute("startAt", null));
    }

    @Test
    void requireDuration_15To480InStepsOf15() {
        for (int ok : new int[]{15, 60, 465, 480}) {
            assertDoesNotThrow(() -> SurveyTimeRules.requireDuration(ok));
        }
        for (int bad : new int[]{0, 10, 50, 495, -15}) {
            assertField("durationMinutes", () -> SurveyTimeRules.requireDuration(bad));
        }
    }

    @Test
    void requireArrivalWindow_notInTheFutureNorBeforeTheCreationMinute() {
        Instant created = Instant.parse("2026-10-23T03:05:30Z");
        assertDoesNotThrow(() -> SurveyTimeRules.requireArrivalWindow(Instant.parse("2026-10-23T03:05:00Z"), NOW,
                created));
        assertDoesNotThrow(() -> SurveyTimeRules.requireArrivalWindow(CURRENT_MINUTE, NOW, created));
        assertField("arrivedAt", () -> SurveyTimeRules.requireArrivalWindow(CURRENT_MINUTE.plusSeconds(60), NOW,
                created));
        assertField("arrivedAt", () -> SurveyTimeRules.requireArrivalWindow(Instant.parse("2026-10-23T03:04:00Z"),
                NOW, created));
    }

    @Test
    void requireArrivalNotTooEarly_atMost60MinutesBeforeTheStart() {
        Instant start = Instant.parse("2026-10-24T03:00:00Z");
        assertDoesNotThrow(() -> SurveyTimeRules.requireArrivalNotTooEarly(Instant.parse("2026-10-24T02:00:00Z"),
                start));
        assertDoesNotThrow(() -> SurveyTimeRules.requireArrivalNotTooEarly(Instant.parse("2026-10-24T02:30:00Z"),
                start));
        assertField("arrivedAt", () -> SurveyTimeRules.requireArrivalNotTooEarly(
                Instant.parse("2026-10-24T01:59:00Z"), start));
    }

    @Test
    void vietnamDate_isTheDateInAsiaHoChiMinh() {
        assertEquals(LocalDate.parse("2026-10-24"), SurveyTimeRules.vietnamDate(Instant.parse("2026-10-23T17:00:00Z")));
        assertEquals(LocalDate.parse("2026-10-23"), SurveyTimeRules.vietnamDate(Instant.parse("2026-10-23T16:59:59Z")));
        assertEquals(Instant.parse("2026-10-23T17:00:00Z"),
                SurveyTimeRules.startOfNextVietnamDay(Instant.parse("2026-10-23T03:00:00Z")));
    }

    private static void assertField(String field, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(field, assertThrows(InvalidSurveyValueException.class, executable).getField());
    }
}
