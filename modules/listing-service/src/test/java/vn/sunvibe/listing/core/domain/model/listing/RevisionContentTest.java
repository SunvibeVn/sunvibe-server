package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored form and the bounds of a revision's text (listing-publish BR-2, BR-3; E06-S2 spec E06S2-D4): NFC, LF line
 * breaks, trimmed, counted in code points; no control or format character.
 */
class RevisionContentTest {

    /** "ộ": one code point in NFC, three in NFD. */
    private static final String O_HOOK = "ộ";
    private static final String EMOJI = "😀";

    @Test
    void normalized_crLfAndCr_lf() {
        assertEquals("a\nb\nc", RevisionContent.normalized("a\r\nb\rc"));
        assertEquals("a\n\nb", RevisionContent.normalized("a\r\n\r\nb"));
    }

    @Test
    void normalized_surroundingSpacesAndNoBreakSpaces_trimmed() {
        assertEquals("Phòng đẹp", RevisionContent.normalized("  \t Phòng đẹp   \r\n"));
        assertEquals("a   b", RevisionContent.normalized(" a   b "));
    }

    @Test
    void normalized_onlySpaces_null() {
        assertNull(RevisionContent.normalized("  \t\r\n "));
        assertNull(RevisionContent.normalized(""));
        assertNull(RevisionContent.normalized(null));
    }

    @Test
    void normalized_nfdVietnamese_nfc() {
        String typed = Normalizer.normalize("Phòng", Normalizer.Form.NFD);
        assertNotEquals("Phòng", typed);

        assertEquals("Phòng", RevisionContent.normalized(typed));
    }

    @Test
    void titleStorable_ninetyTypedInNfd_trueNinetyOneFalse() {
        String ninety = Normalizer.normalize(O_HOOK.repeat(90), Normalizer.Form.NFD);
        String ninetyOne = Normalizer.normalize(O_HOOK.repeat(91), Normalizer.Form.NFD);
        assertEquals(270, ninety.codePointCount(0, ninety.length()));

        assertTrue(RevisionContent.titleStorable(RevisionContent.normalized(ninety)));
        assertFalse(RevisionContent.titleStorable(RevisionContent.normalized(ninetyOne)));
    }

    @Test
    void titleStorable_emojiCountsOne_ninetyAllowed() {
        String title = "a".repeat(89) + EMOJI;
        assertEquals(91, title.length());

        assertEquals(90, RevisionContent.codePoints(title));
        assertTrue(RevisionContent.titleStorable(title));
        assertFalse(RevisionContent.titleStorable("a".repeat(90) + EMOJI));
    }

    @Test
    void titleStorable_lineBreak_false() {
        assertAll(
                () -> assertFalse(RevisionContent.titleStorable("Phòng\nmới")),
                () -> assertFalse(RevisionContent.titleStorable("Phòng mới")),
                () -> assertFalse(RevisionContent.titleStorable("Phòng mới")));
    }

    @Test
    void descriptionStorable_threeThousand_trueAboveFalse() {
        assertTrue(RevisionContent.descriptionStorable("a".repeat(3000)));
        assertFalse(RevisionContent.descriptionStorable("a".repeat(3001)));
        assertTrue(RevisionContent.descriptionStorable("Dòng một\nDòng hai"));
        assertTrue(RevisionContent.descriptionStorable("a".repeat(2999) + EMOJI));
    }

    @Test
    void storable_controlOrFormat_false() {
        for (String character : List.of("\u0000", "\u0007", "​", "﻿", "‮")) {
            String text = "ab" + character + "cd";
            assertFalse(RevisionContent.titleStorable(text), "title " + Integer.toHexString(character.codePointAt(0)));
            assertFalse(RevisionContent.descriptionStorable(text),
                    "description " + Integer.toHexString(character.codePointAt(0)));
        }
    }

    @Test
    void storable_null_true() {
        assertTrue(RevisionContent.titleStorable(null));
        assertTrue(RevisionContent.descriptionStorable(null));
    }

    @Test
    void constructor_unstorableText_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionContent("a".repeat(91), null, RevisionFeatures.NONE_CHOSEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionContent("a\nb", null, RevisionFeatures.NONE_CHOSEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionContent(null, "a\u0000b", RevisionFeatures.NONE_CHOSEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionContent(null, "a".repeat(3001), RevisionFeatures.NONE_CHOSEN)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RevisionContent(null, null, null)));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new RevisionContent("secret title​", null, RevisionFeatures.NONE_CHOSEN));
        assertFalse(String.valueOf(refused.getMessage()).contains("secret"));
    }

    @Test
    void empty_noTextNoFeature() {
        assertNull(RevisionContent.EMPTY.titleOrNull());
        assertNull(RevisionContent.EMPTY.descriptionOrNull());
        assertSame(RevisionFeatures.NONE_CHOSEN, RevisionContent.EMPTY.features());
        assertEquals(90, RevisionContent.TITLE_MAX);
        assertEquals(3000, RevisionContent.DESCRIPTION_MAX);
    }
}
