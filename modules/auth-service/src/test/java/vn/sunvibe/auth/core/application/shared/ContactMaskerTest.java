package vn.sunvibe.auth.core.application.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContactMaskerTest {

    @Test
    void maskPhone() {
        assertEquals("091****678", ContactMasker.maskPhone("0912345678"));
        assertEquals("841******678", ContactMasker.maskPhone("841234567678"));
        assertEquals("****", ContactMasker.maskPhone("123"));
    }

    /** auth-email-access 4.10: the one way an identifier reaches an audit subject. */
    @Test
    void mask_picksTheFormByTheShape() {
        assertEquals("091****678", ContactMasker.mask("0912345678"));
        assertEquals("+84******678", ContactMasker.mask("+84912345678"));
        assertEquals("a***@example.com", ContactMasker.mask("an.nguyen@example.com"));
        assertNull(ContactMasker.mask(null));
    }

    /** A value already masked stays as it is, so a caller may mask first. */
    @Test
    void mask_twice_changesNothing() {
        for (String raw : java.util.List.of("0912345678", "+84912345678", "an.nguyen@example.com", "abc", "x".repeat(60))) {
            String masked = ContactMasker.mask(raw);
            assertEquals(masked, ContactMasker.mask(masked));
        }
    }

    /** PostgreSQL cannot store U+0000, and a login may carry it (SV-087). */
    @Test
    void mask_dropsControlCharacters() {
        assertFalse(ContactMasker.mask("091\u0000234\n5678").matches(".*\\p{Cntrl}.*"));
        assertFalse(ContactMasker.mask("a\u0000b@exa\u0000mple.com").matches(".*\\p{Cntrl}.*"));
    }

    /** The column holds 100 characters, and what a stranger typed has no limit of its own. */
    @Test
    void mask_longValue_isBounded() {
        assertTrue(ContactMasker.mask("9".repeat(500)).length() <= 100);
        assertTrue(ContactMasker.mask("a@" + "b".repeat(500) + ".com").length() <= 100);
    }

    @Test
    void maskEmail() {
        assertEquals("a***@example.com", ContactMasker.maskEmail("an.nguyen@example.com"));
        assertEquals("***", ContactMasker.maskEmail("not-an-email"));
    }
}
