package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome.AVAILABLE;
import static vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome.NOT_AVAILABLE;
import static vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome.UNREACHABLE;

/**
 * One contact with the person letting the unit (listing-publish US-6 AC-1, BR-9, BR-11; E06-S3 spec E06S3-D4,
 * E06S3-D10): the two answers present exactly for "still available", valid only with both answers yes, and a text that
 * names no note, outcome or answer.
 */
class FreshnessRecordTest {

    private static final String ID = "01JGFRESHREC00000000000000";
    private static final String LISTING_ID = "01JGLISTING000000000000000";
    private static final String SALE_ID = "01JGSALE000000000000000000";

    @Test
    void valid_availableWithBothYes_trueElseFalse() {
        assertTrue(record(AVAILABLE, true, true, null).valid());
        assertFalse(record(AVAILABLE, true, false, null).valid());
        assertFalse(record(AVAILABLE, false, true, null).valid());
        assertFalse(record(AVAILABLE, false, false, null).valid());
        assertFalse(record(NOT_AVAILABLE, null, null, null).valid());
        assertFalse(record(UNREACHABLE, null, null, null).valid());
    }

    @Test
    void create_answersNotMatchingOutcome_illegalArgument() {
        List<Runnable> cases = List.of(
                () -> record(AVAILABLE, true, null, null),
                () -> record(UNREACHABLE, true, false, null));
        assertEquals(List.of(IllegalArgumentException.class, IllegalArgumentException.class),
                cases.stream().map(FreshnessRecordTest::thrown).toList());
    }

    @Test
    void create_noteNotStorableOrNotNormalized_illegalArgument() {
        List<Runnable> cases = List.of(
                () -> record(NOT_AVAILABLE, null, null, "a".repeat(501)),
                () -> record(NOT_AVAILABLE, null, null, "  a  "));
        assertEquals(List.of(IllegalArgumentException.class, IllegalArgumentException.class),
                cases.stream().map(FreshnessRecordTest::thrown).toList());
    }

    @Test
    void create_rowVersionBelowOne_illegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new FreshnessRecord(ID, LISTING_ID, 0L,
                LocalDate.of(2027, 3, 1), Instant.parse("2027-03-01T02:10:00Z"), SALE_ID, FreshnessChannel.CALL,
                NOT_AVAILABLE, null, null, null));
    }

    @Test
    void toString_holdsNoNoteOutcomeOrAnswers() {
        List<String> texts = List.of(record(AVAILABLE, true, false, "SECRET-NOTE").toString(),
                record(NOT_AVAILABLE, null, null, "SECRET-NOTE").toString());
        assertEquals(List.of(), Stream.of("SECRET-NOTE", "AVAILABLE", "true", "false")
                .filter(word -> texts.stream().anyMatch(text -> text.contains(word)))
                .toList());
    }

    private static FreshnessRecord record(FreshnessOutcome outcome, Boolean termsStillApply, Boolean unitUnchanged,
                                          String note) {
        return new FreshnessRecord(ID, LISTING_ID, 5L, LocalDate.of(2027, 3, 1),
                Instant.parse("2027-03-01T02:10:00Z"), SALE_ID, FreshnessChannel.CALL, outcome, termsStillApply,
                unitUnchanged, note);
    }

    /** The class of the exception a call throws, or null when it throws none. */
    private static Class<?> thrown(Runnable call) {
        try {
            call.run();
            return null;
        } catch (RuntimeException e) {
            return e.getClass();
        }
    }
}
