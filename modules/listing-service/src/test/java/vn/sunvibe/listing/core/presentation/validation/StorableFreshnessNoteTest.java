package vn.sunvibe.listing.core.presentation.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The note constraint of the freshness record request (listing-publish BR-17; E06-S3 spec E06S3-D10, FR-2): the edge
 * bound of 2,000 UTF-16 units first, before the note is normalized, then the note's storable rule.
 */
class StorableFreshnessNoteTest {

    private final StorableFreshnessNote.Validator validator = new StorableFreshnessNote.Validator();

    @Test
    void isValid_overEdgeUnitsThoughNormalizable_refusedBeforeNormalizing() {
        assertFalse(validator.isValid("a" + "\r\n".repeat(1000), null));
    }

    @Test
    void isValid_storableOrNull_true() {
        assertEquals(List.of(true, true, true), Stream.of(null, "a\nb", "a".repeat(500))
                .map(value -> validator.isValid(value, null))
                .toList());
    }

    @Test
    void isValid_controlFormatOrTooLong_false() {
        assertEquals(List.of(false, false, false), Stream.of("a\u0000b", "a​b", "a".repeat(501))
                .map(value -> validator.isValid(value, null))
                .toList());
    }
}
