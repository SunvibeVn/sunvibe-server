package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The statement an independent manager makes when activating a version (brokerage-agreement BR-11; E05-S6 spec S6-D3):
 * held by the server with its version, naming the version number twice.
 */
class ActivationStatementTest {

    @Test
    void text_versionNo_namedTwice() {
        assertEquals("I compared the signed document with every term of version 2 above: the period, the advertising"
                + " permission, the photo consent, the rental terms and the brokerage fee policy match it. The owner who"
                + " signed it is the individual who confirmed version 2, signing for themselves: no company,"
                + " representative or co-owner signed for them. The document's number, date and Sunvibe's signatory"
                + " below are as written on it.", ActivationStatement.text(2));
    }

    @Test
    void version_currentLabel() {
        assertEquals("brokerage-activation-2026-11", ActivationStatement.VERSION);
    }
}
