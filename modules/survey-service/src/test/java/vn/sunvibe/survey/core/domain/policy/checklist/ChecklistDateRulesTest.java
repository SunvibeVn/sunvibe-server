package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checklist dates on the Vietnam calendar (survey-checklist spec A-7, owner-confirmed at gate 1, C-5). */
class ChecklistDateRulesTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-24");
    private static final LocalDate ARRIVAL = LocalDate.parse("2026-10-20");

    /** Review Focus 2: 17:05Z is 00:05 of the next Vietnam day, and that day is "today". */
    @Test
    void dateRules_vietnamMidnight_acceptVietnamToday() {
        LocalDate today = SurveyTimeRules.vietnamDate(Instant.parse("2026-10-24T17:05:00Z"));

        assertEquals(LocalDate.parse("2026-10-25"), today);
        assertEquals(Optional.empty(), ChecklistDateRules.requireStatementDate("confirmedOn", today, today));
        assertTrue(ChecklistDateRules.requireStatementDate("confirmedOn", today.plusDays(1), today).isPresent());
        assertEquals(Optional.empty(), ChecklistDateRules.requireBasisDate("basis.confirmedOn", today, ARRIVAL,
                today));
    }

    @Test
    void statementDate_floorTodayAndBeforeTheLead() {
        assertEquals(Optional.of(new Violation("content.x.confirmedOn",
                        "content.x.confirmedOn must be between 2000-01-01 and today.")),
                ChecklistDateRules.requireStatementDate("content.x.confirmedOn", LocalDate.parse("1999-12-31"),
                        TODAY));
        assertEquals(Optional.empty(), ChecklistDateRules.requireStatementDate("p", LocalDate.parse("2000-01-01"),
                TODAY));
        // survey-checklist BR-19, W16-7: the owner may have said it before the lead was created
        assertEquals(Optional.empty(), ChecklistDateRules.requireStatementDate("p", LocalDate.parse("2019-03-01"),
                TODAY));
    }

    @Test
    void basisDate_fromTheArrivalDateToToday() {
        assertTrue(ChecklistDateRules.requireBasisDate("p", ARRIVAL.minusDays(1), ARRIVAL, TODAY).isPresent());
        assertEquals(Optional.empty(), ChecklistDateRules.requireBasisDate("p", ARRIVAL, ARRIVAL, TODAY));
        assertEquals(Optional.empty(), ChecklistDateRules.requireBasisDate("p", TODAY, ARRIVAL, TODAY));
        assertTrue(ChecklistDateRules.requireBasisDate("p", TODAY.plusDays(1), ARRIVAL, TODAY).isPresent());
    }

    @Test
    void reportDate_fromTheFirstSightingToToday() {
        LocalDate firstSighting = LocalDate.parse("2026-09-01");

        assertEquals(Optional.empty(), ChecklistDateRules.requireReportDate("p", LocalDate.parse("2026-09-15"),
                firstSighting, TODAY));
        assertTrue(ChecklistDateRules.requireReportDate("p", firstSighting.minusDays(1), firstSighting, TODAY)
                .isPresent());
        assertTrue(ChecklistDateRules.requireReportDate("p", TODAY.plusDays(1), firstSighting, TODAY).isPresent());
    }

    @Test
    void futureDate_upToThreeYearsAhead() {
        assertEquals(Optional.empty(), ChecklistDateRules.requireFutureDate("p", TODAY.plusYears(3), TODAY));
        assertTrue(ChecklistDateRules.requireFutureDate("p", TODAY.plusYears(3).plusDays(1), TODAY).isPresent());
        assertEquals(Optional.empty(), ChecklistDateRules.requireFutureDate("p", LocalDate.parse("2000-01-01"),
                TODAY));
        assertTrue(ChecklistDateRules.requireFutureDate("p", LocalDate.parse("1999-12-31"), TODAY).isPresent());
    }
}
