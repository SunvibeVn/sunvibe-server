package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The signed document an activation records (brokerage-agreement BR-11; E05-S6 spec S6-D3): its number, signing date and
 * Sunvibe's signatory as written on it, trimmed, under the character rule of S4-D3, and never a value in a text.
 */
class SignedDocumentTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-18");
    private static final String NUMBER = "HDMG-2026/014";
    private static final LocalDate SIGNED_ON = LocalDate.parse("2026-11-16");
    private static final String NAME = "Nguyen Van A";
    private static final String TITLE = "Director";

    @Test
    void of_valuesTrimmed_kept() {
        SignedDocument document = SignedDocument.of("  " + NUMBER + "  ", SIGNED_ON, " " + NAME + " ", "\t" + TITLE + " ",
                TODAY);

        assertEquals(new SignedDocument(NUMBER, SIGNED_ON, NAME, TITLE), document);
        assertEquals(50, SignedDocument.of("a".repeat(50), SIGNED_ON, NAME, TITLE, TODAY).number().length());
        assertEquals(100, SignedDocument.of(NUMBER, SIGNED_ON, "a".repeat(100), "b".repeat(100), TODAY)
                .sunvibeSignatoryTitle().length());
    }

    @Test
    void of_blankOrTooLong_refusedOnItsField() {
        for (String bad : new String[]{null, "", "   ", "a".repeat(51)}) {
            assertEquals("documentNumber", refused(() -> SignedDocument.of(bad, SIGNED_ON, NAME, TITLE, TODAY)),
                    String.valueOf(bad));
        }
        for (String bad : new String[]{null, "", "   ", "a".repeat(101)}) {
            assertEquals("sunvibeSignatoryName", refused(() -> SignedDocument.of(NUMBER, SIGNED_ON, bad, TITLE, TODAY)),
                    String.valueOf(bad));
            assertEquals("sunvibeSignatoryTitle", refused(() -> SignedDocument.of(NUMBER, SIGNED_ON, NAME, bad, TODAY)),
                    String.valueOf(bad));
        }
    }

    @Test
    void of_hiddenCharacter_refusedOnItsField() {
        for (String hidden : List.of("\u0000", "\u001F", "‮", "⁦")) {
            String text = "A" + hidden + "B";
            assertEquals("documentNumber", refused(() -> SignedDocument.of(text, SIGNED_ON, NAME, TITLE, TODAY)));
            assertEquals("sunvibeSignatoryName", refused(() -> SignedDocument.of(NUMBER, SIGNED_ON, text, TITLE, TODAY)));
            assertEquals("sunvibeSignatoryTitle", refused(() -> SignedDocument.of(NUMBER, SIGNED_ON, NAME, text, TODAY)));
        }
    }

    @Test
    void of_signedOnOutsideTheWindow_refused() {
        assertEquals("signedOn", refused(() -> SignedDocument.of(NUMBER, LocalDate.parse("1999-12-31"), NAME, TITLE,
                TODAY)));
        assertEquals("signedOn", refused(() -> SignedDocument.of(NUMBER, TODAY.plusDays(1), NAME, TITLE, TODAY)));
        assertEquals("signedOn", refused(() -> SignedDocument.of(NUMBER, null, NAME, TITLE, TODAY)));

        assertEquals(LocalDate.parse("2000-01-01"),
                SignedDocument.of(NUMBER, LocalDate.parse("2000-01-01"), NAME, TITLE, TODAY).signedOn());
        assertEquals(TODAY, SignedDocument.of(NUMBER, TODAY, NAME, TITLE, TODAY).signedOn());
    }

    /** Review Focus 1: at 00:30 in Vietnam UTC is still the day before; today is Vietnam's date, not UTC's. */
    @Test
    void of_signedOnTodayInVietnamWhileUtcYesterday_accepted() {
        Instant now = Instant.parse("2026-11-17T17:30:00Z");
        LocalDate todayInVietnam = LocalDate.ofInstant(now, ZoneId.of("Asia/Ho_Chi_Minh"));

        assertEquals(LocalDate.parse("2026-11-18"), todayInVietnam);
        assertEquals(LocalDate.parse("2026-11-18"),
                SignedDocument.of(NUMBER, LocalDate.parse("2026-11-18"), NAME, TITLE, todayInVietnam).signedOn());
        assertEquals("signedOn", refused(() -> SignedDocument.of(NUMBER, LocalDate.parse("2026-11-19"), NAME, TITLE,
                todayInVietnam)));
    }

    /** brokerage-agreement BR-17: the document's values stay out of every log line. */
    @Test
    void toString_hidesValues() {
        String text = new SignedDocument(NUMBER, SIGNED_ON, NAME, TITLE).toString();

        assertFalse(text.contains(NUMBER), text);
        assertFalse(text.contains(NAME), text);
        assertFalse(text.contains(TITLE), text);
        assertFalse(text.contains("2026-11-16"), text);
        assertTrue(text.startsWith("SignedDocument"), text);
    }

    private static String refused(Executable call) {
        return assertThrows(AgreementContentException.class, call).field();
    }
}
