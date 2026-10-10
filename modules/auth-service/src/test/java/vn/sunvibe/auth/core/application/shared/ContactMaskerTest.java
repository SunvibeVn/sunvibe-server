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

    @Test
    void maskEmail() {
        assertEquals("a***@example.com", ContactMasker.maskEmail("an.nguyen@example.com"));
        assertEquals("***", ContactMasker.maskEmail("not-an-email"));
    }
}
