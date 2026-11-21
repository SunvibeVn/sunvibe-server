package vn.sunvibe.listing.core.presentation.validation;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text rules of a save, answered at the request's shape step (listing-publish BR-2; E06-S2 spec E06S2-D4): the
 * text as it would be stored, at most 90 or 3,000 code points, one line for a title, no control or format character.
 */
class StorableListingTextTest {

    private final StorableListingTitle.Validator title = new StorableListingTitle.Validator();
    private final StorableListingDescription.Validator description = new StorableListingDescription.Validator();

    @Test
    void title_ninetyAfterNfcTyped_valid() {
        String typed = Normalizer.normalize("ộ".repeat(90), Normalizer.Form.NFD);

        assertTrue(title.isValid(typed, null));
        assertTrue(title.isValid("  " + "a".repeat(90) + "  ", null));
        assertTrue(title.isValid("a".repeat(89) + "😀", null));
    }

    @Test
    void title_lineBreakControlFormatOrNinetyOne_invalid() {
        for (String value : List.of("a".repeat(91), "Phòng\nmới", "Phòng\r\nmới", "a b",
                "a\u0000b", "a\u0007b", "a​b", "a﻿b", "a‮b",
                Normalizer.normalize("ộ".repeat(91), Normalizer.Form.NFD))) {
            assertFalse(title.isValid(value, null), Integer.toString(value.length()));
        }
    }

    @Test
    void description_threeThousandOne_invalid() {
        assertFalse(description.isValid("a".repeat(3001), null));
        assertFalse(description.isValid("a\u0000b", null));
        assertFalse(description.isValid("a​b", null));
        assertTrue(description.isValid("a".repeat(3000), null));
    }

    @Test
    void description_lineFeed_valid() {
        assertTrue(description.isValid("Dòng một\nDòng hai\r\nDòng ba", null));
    }

    @Test
    void nullValues_valid() {
        assertTrue(title.isValid(null, null));
        assertTrue(description.isValid(null, null));
        assertTrue(title.isValid("   ", null));
    }
}
