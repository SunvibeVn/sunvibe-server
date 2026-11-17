package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The confirmation statement the server holds (brokerage-agreement BR-10; E05-S5 spec S5-D3): one text with a version,
 * built for a version with its number and its unit's code.
 */
class AgreementStatementTest {

    private static final String REST = ": I have read every term above. Sunvibe may advertise this unit on Sunvibe's own"
            + " channels under these terms and for this period. I declare that I have the right to let this unit and to"
            + " allow it to be advertised. The written agreement that Sunvibe and I sign is the binding document.";

    @Test
    void text_versionAndCode_exactWords() {
        assertEquals("Version 2 of the brokerage agreement for unit P101" + REST, AgreementStatement.text(2, "P101"));
    }

    @Test
    void text_noCode_forThisUnit() {
        assertEquals("Version 1 of the brokerage agreement for this unit" + REST, AgreementStatement.text(1, null));
    }

    @Test
    void version_fixedValue() {
        assertEquals("brokerage-agreement-2026-11", AgreementStatement.VERSION);
    }
}
