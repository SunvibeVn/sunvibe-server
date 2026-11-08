package vn.sunvibe.property.core.domain.policy.intake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The normalized address of property-create intake spec A-8, compared by the duplicate signal (D5-14). */
class AddressNormalizerTest {

    @Test
    void normalize_vietnameseMarks() {
        assertEquals("1 bach dang", AddressNormalizer.normalize("1 Bạch Đằng"));
        assertEquals("duong le duan", AddressNormalizer.normalize("ĐƯỜNG LÊ DUẨN"));
    }

    /** A precomposed and a decomposed spelling of one address are the same address. */
    @Test
    void normalize_composedAndDecomposed_equal() {
        assertEquals("hai chau", AddressNormalizer.normalize("Hải Châu"));
        assertEquals("hai chau", AddressNormalizer.normalize("Hải Châu"));
    }

    @Test
    void normalize_punctuationToSpaceAndSpacesCollapsed() {
        assertEquals("12 3 nguyen van linh p hai chau",
                AddressNormalizer.normalize("  12/3,  Nguyễn Văn   Linh - P.Hải Châu "));
    }

    @Test
    void normalize_longText_cutTo255() {
        assertEquals(255, AddressNormalizer.normalize("a".repeat(300)).length());
    }

    /** A cut never leaves a trailing space, so two equal prefixes stay equal. */
    @Test
    void normalize_cutAtASpace_noTrailingSpace() {
        String text = "a".repeat(254) + " b";

        assertEquals("a".repeat(254), AddressNormalizer.normalize(text));
    }

    @Test
    void normalize_null_null() {
        assertNull(AddressNormalizer.normalize(null));
    }
}
