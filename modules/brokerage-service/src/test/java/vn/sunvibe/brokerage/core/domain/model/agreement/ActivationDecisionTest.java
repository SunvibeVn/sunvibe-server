package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A manager's decision on a version awaiting activation (brokerage-agreement US-3 AC-1 to AC-3, BR-11, BR-17; E05-S6
 * spec S6-D3, S6-D4, S6-D14): an activation holds the statement version and the signed document and no refusal part; a
 * refusal holds its type and an optional private note, and no statement or document.
 */
class ActivationDecisionTest {

    private static final Instant AT = Instant.parse("2026-11-18T03:00:00.123456Z");
    private static final String STATEMENT = ActivationStatement.VERSION;
    private static final SignedDocument DOCUMENT = new SignedDocument("HDMG-2026/014", LocalDate.parse("2026-11-16"),
            "Nguyen Van A", "Director");

    @Test
    void activated_needsStatementAndDocument_noRefusalPart() {
        assertEquals(new ActivationDecision(ActivationDecisionType.ACTIVATED, "M1", AT, STATEMENT, DOCUMENT, null, null),
                ActivationDecision.activated("M1", AT, STATEMENT, DOCUMENT));

        List<Executable> broken = List.of(
                () -> new ActivationDecision(ActivationDecisionType.ACTIVATED, "M1", AT, null, DOCUMENT, null, null),
                () -> new ActivationDecision(ActivationDecisionType.ACTIVATED, "M1", AT, STATEMENT, null, null, null),
                () -> new ActivationDecision(ActivationDecisionType.ACTIVATED, "M1", AT, STATEMENT, DOCUMENT,
                        ActivationRefusalType.DOCUMENT_DIFFERS, null),
                () -> new ActivationDecision(ActivationDecisionType.ACTIVATED, "M1", AT, STATEMENT, DOCUMENT, null,
                        "A note"),
                () -> new ActivationDecision(null, "M1", AT, STATEMENT, DOCUMENT, null, null),
                () -> new ActivationDecision(ActivationDecisionType.ACTIVATED, null, AT, STATEMENT, DOCUMENT, null, null),
                () -> new ActivationDecision(ActivationDecisionType.ACTIVATED, "M1", null, STATEMENT, DOCUMENT, null,
                        null));
        for (int index = 0; index < broken.size(); index++) {
            assertThrows(IllegalArgumentException.class, broken.get(index), "case " + index);
        }
    }

    @Test
    void refused_needsType_noStatementOrDocument() {
        assertEquals(new ActivationDecision(ActivationDecisionType.REFUSED, "M1", AT, null, null,
                        ActivationRefusalType.SIGNER_NOT_OWNER, null),
                ActivationDecision.refused("M1", AT, ActivationRefusalType.SIGNER_NOT_OWNER, null));

        List<Executable> broken = List.of(
                () -> new ActivationDecision(ActivationDecisionType.REFUSED, "M1", AT, null, null, null, null),
                () -> new ActivationDecision(ActivationDecisionType.REFUSED, "M1", AT, STATEMENT, null,
                        ActivationRefusalType.DOCUMENT_DIFFERS, null),
                () -> new ActivationDecision(ActivationDecisionType.REFUSED, "M1", AT, null, DOCUMENT,
                        ActivationRefusalType.DOCUMENT_DIFFERS, null));
        for (int index = 0; index < broken.size(); index++) {
            assertThrows(IllegalArgumentException.class, broken.get(index), "case " + index);
        }
    }

    @Test
    void refused_noteTrimmedBlankOrNull_noneStored() {
        assertEquals("Rent differs.", ActivationDecision.refused("M1", AT, ActivationRefusalType.DOCUMENT_DIFFERS,
                "  Rent differs.  ").refusalNoteOrNull());
        for (String none : Arrays.asList(null, "", "   ")) {
            assertNull(ActivationDecision.refused("M1", AT, ActivationRefusalType.DOCUMENT_DIFFERS, none)
                    .refusalNoteOrNull(), String.valueOf(none));
        }
        String fiveHundred = "a".repeat(500);
        assertEquals(fiveHundred, ActivationDecision.refused("M1", AT, ActivationRefusalType.DOCUMENT_DIFFERS,
                fiveHundred).refusalNoteOrNull());
    }

    @Test
    void refused_noteTooLongOrHidden_refusedOnNote() {
        for (String note : List.of("a".repeat(501), "Rent‮differs", "Rent\u0000differs")) {
            AgreementContentException refused = assertThrows(AgreementContentException.class,
                    () -> ActivationDecision.refused("M1", AT, ActivationRefusalType.DOCUMENT_DIFFERS, note));
            assertEquals("note", refused.field());
        }
    }

    /** brokerage-agreement BR-17: the document, the note, the account and the statement stay out of every log line. */
    @Test
    void toString_hidesDocumentAndNote() {
        List<String> texts = List.of(ActivationDecision.activated("01JMANAGER", AT, STATEMENT, DOCUMENT).toString(),
                ActivationDecision.refused("01JMANAGER", AT, ActivationRefusalType.DOCUMENT_DIFFERS, "Secret note")
                        .toString());

        for (String text : texts) {
            assertFalse(text.contains("Secret"), text);
            assertFalse(text.contains("01JMANAGER"), text);
            assertFalse(text.contains("HDMG"), text);
            assertFalse(text.contains("Nguyen"), text);
            assertFalse(text.contains("Director"), text);
            assertFalse(text.contains(STATEMENT), text);
        }
        assertEquals("ActivationDecision[ACTIVATED]", texts.get(0));
        assertEquals("ActivationDecision[REFUSED, DOCUMENT_DIFFERS, note]", texts.get(1));
    }

    @Test
    void types_of_storedCodes() {
        assertEquals(ActivationDecisionType.ACTIVATED, ActivationDecisionType.of("ACTIVATED").orElseThrow());
        assertEquals(ActivationDecisionType.REFUSED, ActivationDecisionType.of("REFUSED").orElseThrow());
        assertNull(ActivationDecisionType.of("activated").orElse(null));
        assertEquals(ActivationRefusalType.DOCUMENT_DIFFERS, ActivationRefusalType.of("DOCUMENT_DIFFERS").orElseThrow());
        assertEquals(ActivationRefusalType.SIGNER_NOT_OWNER, ActivationRefusalType.of("SIGNER_NOT_OWNER").orElseThrow());
        assertNull(ActivationRefusalType.of("OTHER").orElse(null));
        assertNull(ActivationRefusalType.of(null).orElse(null));
    }
}
