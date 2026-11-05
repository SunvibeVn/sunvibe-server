package vn.sunvibe.auth.core.domain.model.user.vo;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.testsupport.IdentifierCorpus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * auth-email-access S-1: every corpus case gives its expected result through the value objects, and the corpus is the
 * byte-for-byte copy the web app tests against too.
 */
class IdentifierCorpusTest {

    private static final String CORPUS_SHA256 = "a494b197efb848ed2754fcb0b6386b5a30aad6e6fe4a26aff7d9f64b051b46a7";

    @Test
    void corpus_hashPinned() throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(IdentifierCorpus.bytes());

        assertEquals(CORPUS_SHA256, HexFormat.of().formatHex(digest));
    }

    @Test
    void corpus_email_everyCase() {
        assertEquals(56, IdentifierCorpus.email().size());
        for (IdentifierCorpus.Case c : IdentifierCorpus.email()) {
            if (c.valid()) {
                assertEquals(c.canonical(), Email.create(c.raw()).getValue(), c.id() + " " + c.note());
            } else {
                assertThrows(AuthValidationException.class, () -> Email.create(c.raw()), c.id() + " " + c.note());
            }
        }
    }

    @Test
    void corpus_phone_everyCase() {
        assertEquals(42, IdentifierCorpus.phone().size());
        for (IdentifierCorpus.Case c : IdentifierCorpus.phone()) {
            if (c.valid()) {
                PhoneNumber phone = PhoneNumber.create(c.raw());
                assertEquals(c.canonical(), phone.key(), c.id() + " " + c.note());
                assertEquals(c.national(), phone.national(), c.id() + " " + c.note());
            } else {
                assertThrows(AuthValidationException.class, () -> PhoneNumber.create(c.raw()), c.id() + " " + c.note());
            }
        }
    }
}
