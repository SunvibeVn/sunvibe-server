package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The note of a review decision (listing-publish E06-S4 spec E06S4-D6, ASSUMPTION A-2): stored as a revision text is,
 * counted in code points, 1 to 1,000 of them, no control or format character but LF, and never shown by its text.
 */
class ReviewNoteTest {

    private static final String SMILE = new String(Character.toChars(0x1F600));

    @Test
    void ofOrNull_crlfAndTrim_storedNormalized() {
        ReviewNote note = ReviewNote.ofOrNull("  Sửa tiêu đề\r\nvà mô tả\r\n\r\n");

        assertEquals("Sửa tiêu đề\nvà mô tả", note.text());
        assertEquals("a\nb", ReviewNote.ofOrNull("a\rb").text());
    }

    @Test
    void ofOrNull_blank_null() {
        assertNull(ReviewNote.ofOrNull(null));
        assertNull(ReviewNote.ofOrNull(""));
        assertNull(ReviewNote.ofOrNull(" \r\n\t  "));
    }

    @Test
    void storable_thousandEmoji_trueThousandAndOne_false() {
        String thousand = SMILE.repeat(1000);
        String thousandAndOne = SMILE.repeat(1001);

        assertEquals(2000, thousand.length());
        assertTrue(ReviewNote.storable(thousand));
        assertEquals(thousand, ReviewNote.ofOrNull(thousand).text());
        assertFalse(ReviewNote.storable(thousandAndOne));
        assertThrows(IllegalArgumentException.class, () -> ReviewNote.ofOrNull(thousandAndOne));
        assertThrows(IllegalArgumentException.class, () -> new ReviewNote(thousandAndOne));
        assertEquals(4000, ReviewNote.EDGE_UNITS);
        assertEquals(1000, ReviewNote.MAX_CODE_POINTS);
    }

    @Test
    void storable_controlOrFormatCharacter_false() {
        assertFalse(ReviewNote.storable("Ghi chú \u0007 sai"));
        assertFalse(ReviewNote.storable("Ghi​chú"));
        assertTrue(ReviewNote.storable("Dòng một\nDòng hai"));
        assertThrows(IllegalArgumentException.class, () -> ReviewNote.ofOrNull("Ghi chú \u0007 sai"));
        assertThrows(IllegalArgumentException.class, () -> new ReviewNote(" untrimmed"));
    }

    @Test
    void toString_namesNoText() {
        ReviewNote note = ReviewNote.ofOrNull("Số điện thoại trong mô tả");

        assertEquals("ReviewNote[hidden]", note.toString());
        assertFalse(note.toString().contains("điện"));
    }
}
