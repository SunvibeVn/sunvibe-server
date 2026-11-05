package vn.sunvibe.auth.core.infrastructure.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateTest {

    /** The old template linked to a domain that was never ours (auth-email-access spec 3). */
    @Test
    void template_noPlaceholderDomain() {
        String html = EmailTemplate.getPasswordResetTemplate("123456", "https://www.sunvibe.vn/reset-password#token=abc");

        assertFalse(html.contains("yourapp.com"));
        assertTrue(html.contains("href=\"https://www.sunvibe.vn/reset-password#token=abc\""));
        assertTrue(html.contains("123456"));
    }

    /** A percent sign in a link must reach the mail as it is, not be read as a format. */
    @Test
    void template_linkWithPercent_kept() {
        String html = EmailTemplate.getPasswordResetTemplate("123456", "https://www.sunvibe.vn/x%20y");

        assertTrue(html.contains("https://www.sunvibe.vn/x%20y"));
    }
}
