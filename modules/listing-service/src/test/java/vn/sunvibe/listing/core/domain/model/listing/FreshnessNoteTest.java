package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The private note of a freshness record (listing-publish BR-17; E06-S3 spec E06S3-D10, ASSUMPTION A-1): stored in
 * E06S2-D4's normalized form, at most 500 code points, no control character but LF and no format character.
 */
class FreshnessNoteTest {

    /** U+1F600: one code point, two UTF-16 units. */
    private static final String EMOJI = "😀";

    @Test
    void normalized_crLfTrimAndEmpty_normalizedOrNull() {
        assertEquals("a\nb", FreshnessNote.normalized("  a\r\nb\r  "));
        assertNull(FreshnessNote.normalized("\r\n \t"));
        assertEquals("é", FreshnessNote.normalized("é"));
    }

    @Test
    void storable_fiveHundredCodePoints_trueAndOneMore_false() {
        assertFalse(FreshnessNote.storable("a".repeat(501)));
        assertTrue(FreshnessNote.storable("a".repeat(500)));
        assertFalse(FreshnessNote.storable(EMOJI.repeat(501)));
        assertTrue(FreshnessNote.storable(EMOJI.repeat(500)));
    }

    @Test
    void storable_controlOrFormatCharacter_false() {
        assertEquals(List.of(false, false, false, false),
                Stream.of("\u0000", "\u0007", "​", "﻿")
                        .map(character -> FreshnessNote.storable("a" + character + "b"))
                        .toList());
        assertTrue(FreshnessNote.storable("a\nb"));
    }

    @Test
    void ofOrNull_whitespaceOnly_null() {
        assertNull(FreshnessNote.ofOrNull(" \r\n\t "));
    }

    @Test
    void toString_hidesText() {
        assertFalse(FreshnessNote.ofOrNull("SECRET-NOTE").toString().contains("SECRET-NOTE"));
    }
}
