package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The period of an agreement version (brokerage-agreement BR-6; E05-S2 spec S2-D6): both dates required, the end not
 * before the start and at most 12 months after it, as {@code plusMonths(12)} clamps; both dates in 2000..2099.
 */
class AgreementPeriodTest {

    @Test
    void period_endEqualsStart_accepted() {
        AgreementPeriod period = new AgreementPeriod(date("2027-01-01"), date("2027-01-01"));

        assertEquals(date("2027-01-01"), period.startDate());
        assertEquals(date("2027-01-01"), period.endDate());
    }

    @Test
    void period_twelveMonths_boundaries() {
        assertDoesNotThrow(() -> new AgreementPeriod(date("2027-01-01"), date("2028-01-01")));
        assertDoesNotThrow(() -> new AgreementPeriod(date("2028-02-29"), date("2029-02-28")));
        assertDoesNotThrow(() -> new AgreementPeriod(date("2026-01-31"), date("2027-01-31")));

        assertEquals("endDate", refusedField("2027-01-01", "2028-01-02"));
        assertEquals("endDate", refusedField("2028-02-29", "2029-03-01"));
    }

    @Test
    void period_endBeforeStart_endDate() {
        assertEquals("endDate", refusedField("2027-01-02", "2027-01-01"));
    }

    @Test
    void period_outsideTheCentury_fieldOfTheDate() {
        assertEquals("startDate", refusedField("1999-12-31", "2000-06-01"));
        assertEquals("endDate", refusedField("2099-06-01", "2100-01-01"));
        assertDoesNotThrow(() -> new AgreementPeriod(date("2099-01-01"), date("2099-12-31")));
    }

    @Test
    void period_missingDate_itsField() {
        AgreementPeriodException noStart = assertThrows(AgreementPeriodException.class,
                () -> new AgreementPeriod(null, date("2027-01-01")));
        AgreementPeriodException noEnd = assertThrows(AgreementPeriodException.class,
                () -> new AgreementPeriod(date("2027-01-01"), null));

        assertEquals("startDate", noStart.field());
        assertEquals("endDate", noEnd.field());
        assertFalse(noStart.getMessage().matches(".*\\d.*"), noStart.getMessage());
        assertFalse(noEnd.getMessage().matches(".*\\d.*"), noEnd.getMessage());
    }

    private static String refusedField(String start, String end) {
        return assertThrows(AgreementPeriodException.class, () -> new AgreementPeriod(date(start), date(end)),
                start + ".." + end).field();
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }
}
