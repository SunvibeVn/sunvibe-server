package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The freshness dates in Vietnam time (listing-publish BR-10 and its example; E06-S3 spec E06S3-D5, E06S3-D14): fixed
 * instants only, so each boundary is checked at its last microsecond before and its first instant.
 */
class FreshnessPolicyTest {

    private static final LocalDate CONFIRMED_ON = LocalDate.of(2027, 3, 1);
    private static final FreshnessSummary VALID = new FreshnessSummary(CONFIRMED_ON, true);

    @Test
    void dates_briefExample_reconfirmFromAndValidThrough() {
        assertEquals(LocalDate.of(2027, 3, 11), FreshnessPolicy.reconfirmFrom(CONFIRMED_ON));
        assertEquals(LocalDate.of(2027, 3, 15), FreshnessPolicy.validThrough(CONFIRMED_ON));
        assertEquals(Instant.parse("2027-03-15T17:00:00Z"), FreshnessPolicy.deadline(CONFIRMED_ON));
    }

    @Test
    void reconfirmationDue_lastMicroBeforeAndFirstInstantOfTenthDay() {
        assertTrue(FreshnessPolicy.reconfirmationDue(VALID, Instant.parse("2027-03-10T17:00:00Z")));
        assertFalse(FreshnessPolicy.reconfirmationDue(VALID, Instant.parse("2027-03-10T16:59:59.999999Z")));
    }

    @Test
    void deadlinePassed_lastMicroBeforeAndFirstInstantAfterFourteenthDay() {
        assertTrue(FreshnessPolicy.deadlinePassed(VALID, Instant.parse("2027-03-15T17:00:00Z")));
        assertFalse(FreshnessPolicy.deadlinePassed(VALID, Instant.parse("2027-03-15T16:59:59.999999Z")));
    }

    @Test
    void holds_validLatestBeforeDeadlineOnly() {
        assertTrue(FreshnessPolicy.holds(VALID, Instant.parse("2027-03-15T16:59:59.999999Z")));
        assertFalse(FreshnessPolicy.holds(VALID, Instant.parse("2027-03-15T17:00:00Z")));
        assertFalse(FreshnessPolicy.holds(new FreshnessSummary(CONFIRMED_ON, false),
                Instant.parse("2027-03-15T16:59:59.999999Z")));
    }

    @Test
    void flag_staysOnAfterDeadline() {
        assertTrue(FreshnessPolicy.reconfirmationDue(VALID, Instant.parse("2027-04-30T00:00:00Z")));
    }

    @Test
    void noValidConfirmation_noFlagNoPassedNoHold() {
        Instant now = Instant.parse("2027-03-20T00:00:00Z");
        List<FreshnessSummary> summaries = List.of(FreshnessSummary.none(), new FreshnessSummary(null, false));
        assertEquals(List.of(false, false, false, false, false, false), summaries.stream()
                .flatMap(summary -> List.of(FreshnessPolicy.reconfirmationDue(summary, now),
                        FreshnessPolicy.deadlinePassed(summary, now), FreshnessPolicy.holds(summary, now)).stream())
                .toList());
    }

    @Test
    void today_vietnamDateOfInstant() {
        assertEquals(LocalDate.of(2027, 3, 10), FreshnessPolicy.today(Instant.parse("2027-03-10T16:59:59Z")));
        assertEquals(LocalDate.of(2027, 3, 11), FreshnessPolicy.today(Instant.parse("2027-03-10T17:00:00Z")));
    }

    @Test
    void dueOnOrBefore_tenDaysBeforeToday() {
        assertEquals(LocalDate.of(2027, 3, 1), FreshnessPolicy.dueOnOrBefore(Instant.parse("2027-03-10T17:00:00Z")));
    }
}
