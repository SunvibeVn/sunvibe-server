package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A listing's freshness summary (listing-publish BR-9, BR-11; E06-S3 spec E06S3-D6): only a valid record sets the
 * confirmation date; an invalid one keeps the date and marks the latest record invalid.
 */
class FreshnessSummaryTest {

    private static final LocalDate CONFIRMED_ON = LocalDate.of(2027, 3, 1);
    private static final LocalDate LATER = LocalDate.of(2027, 3, 9);

    @Test
    void none_noDateNoLatest() {
        assertEquals(new FreshnessSummary(null, null), FreshnessSummary.none());
    }

    @Test
    void after_validRecord_dateAndValid() {
        assertEquals(new FreshnessSummary(LATER, true),
                FreshnessSummary.none().after(record(LATER, FreshnessOutcome.AVAILABLE, true, true)));
    }

    @Test
    void after_invalidRecord_keepsDateMarksInvalid() {
        FreshnessRecord invalid = record(LATER, FreshnessOutcome.NOT_AVAILABLE, null, null);
        FreshnessSummary fromValid = new FreshnessSummary(CONFIRMED_ON, true).after(invalid);
        assertEquals(Boolean.FALSE, fromValid.latestValidOrNull());
        assertEquals(CONFIRMED_ON, fromValid.confirmedOnOrNull());
        FreshnessSummary fromNone = FreshnessSummary.none().after(invalid);
        assertEquals(Boolean.FALSE, fromNone.latestValidOrNull());
        assertNull(fromNone.confirmedOnOrNull());
    }

    @Test
    void create_latestValidWithoutDate_illegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new FreshnessSummary(null, true));
    }

    private static FreshnessRecord record(LocalDate contactDate, FreshnessOutcome outcome, Boolean termsStillApply,
                                          Boolean unitUnchanged) {
        return new FreshnessRecord("01JGFRESHREC00000000000000", "01JGLISTING000000000000000", 3L, contactDate,
                Instant.parse("2027-03-09T03:00:00Z"), "01JGSALE000000000000000000", FreshnessChannel.MESSAGE,
                outcome, termsStillApply, unitUnchanged, null);
    }
}
