package vn.sunvibe.survey.core.domain.policy;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Free text of survey commands (survey-schedule BR-4, spec A-14, A-24). */
class SurveyTextTest {

    @Test
    void optional_blank_isAbsent() {
        assertNull(SurveyText.optional("accessNote", null, 1000));
        assertNull(SurveyText.optional("accessNote", "   \n ", 1000));
    }

    @Test
    void require_cleansAndKeepsInnerLines() {
        assertEquals("Line one\n  café", SurveyText.require("note", "  Line one\n  café  ", 2000));
    }

    @Test
    void require_blankOrTooLong_isRefusedOnTheField() {
        assertField("reason", () -> SurveyText.require("reason", null, 500));
        assertField("reason", () -> SurveyText.require("reason", "  ", 500));
        assertField("reason", () -> SurveyText.require("reason", "x".repeat(501), 500));
        assertEquals(500, SurveyText.require("reason", "x".repeat(500), 500).length());
    }

    @Test
    void require_andOptional_refusePhoneNumbers() {
        for (String text : new String[]{"0905 123 456", "+84.905.123.456", "(+84) 905 123 456", "0236 3812 345"}) {
            assertField("accessNote", () -> SurveyText.optional("accessNote", "call " + text, 1000));
            assertField("note", () -> SurveyText.require("note", text, 2000));
        }
        assertEquals("tầng 12, căn 1203", SurveyText.require("accessNote", "tầng 12, căn 1203", 1000));
        assertEquals("K12/5 Lê Duẩn", SurveyText.require("meetingLocation.addressText", "K12/5 Lê Duẩn", 255));
    }

    @Test
    void refusal_messageNeverQuotesTheText() {
        InvalidSurveyValueException ex = assertThrows(InvalidSurveyValueException.class,
                () -> SurveyText.require("note", "secret 0905123456", 2000));
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void system_keepsAnyTextUpToTheLimit() {
        assertEquals("call 0905 123 456", SurveyText.system(" call 0905 123 456 ", 2000));
        assertNull(SurveyText.system("  ", 2000));
        assertThrows(IllegalArgumentException.class, () -> SurveyText.system("x".repeat(2001), 2000));
    }

    private static void assertField(String field, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(field, assertThrows(InvalidSurveyValueException.class, executable).getField());
    }
}
