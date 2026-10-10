package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.common.crypto.AesGcmCryptoService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({PasswordResetTokenRepositoryAdapter.class, AesGcmCryptoService.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class PasswordResetTokenRepositoryAdapterTest {

    private static final String EMAIL = "a@example.com";

    @Autowired
    private PasswordResetTokenRepositoryAdapter adapter;

    private static PasswordResetToken token(long id, String value, Instant createdAt) {
        return PasswordResetToken.restore(id, value, "user-1", VerificationChannel.EMAIL, EMAIL, createdAt,
                createdAt.plusSeconds(900), false, null, 0, "123456");
    }

    /** Regression: expiresAt used to be dropped on save, so issuing any reset code failed. */
    @Test
    void save_thenFind_keepsEveryFieldIncludingExpiry() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        PasswordResetToken token = token(1L, "token-1", now);
        token.registerFailedAttempt(5, now);

        adapter.save(token);
        PasswordResetToken found = adapter.findByTokenForUpdate("token-1").orElseThrow();

        assertEquals(now.plusSeconds(900), found.getExpiresAt());
        assertEquals(now, found.getCreatedAt());
        assertEquals(1, found.getFailedAttempts());
        assertEquals(VerificationChannel.EMAIL, found.getChannel());
        assertEquals(EMAIL, found.getDestination());
        assertEquals("123456", found.getOtpCode());
        assertTrue(found.isValid());
    }

    @Test
    void findLatestByEmail_returnsTheNewestEvenWithSeveralUnusedTokens() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        adapter.save(token(1L, "older", now.minusSeconds(300)));
        adapter.save(token(2L, "newer", now));

        assertEquals("newer", adapter.findLatestByDestination(EMAIL).orElseThrow().getToken());
        assertTrue(adapter.findLatestByDestination("other@example.com").isEmpty());
        assertEquals(2, adapter.countIssuedSince(EMAIL, now.minusSeconds(3600)));
        assertEquals(1, adapter.countIssuedSince(EMAIL, now.minusSeconds(60)));
    }

    @Test
    void invalidateActiveTokens_burnsOnlyThatEmailsUnusedTokens() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        adapter.save(token(1L, "mine", now));
        adapter.save(PasswordResetToken.restore(2L, "theirs", "user-2", VerificationChannel.PHONE, "0987654321", now,
                now.plusSeconds(900), false, null, 0, "654321"));

        adapter.invalidateActiveTokens(EMAIL, now);

        assertFalse(adapter.findByTokenForUpdate("mine").orElseThrow().isValid());
        assertTrue(adapter.findByTokenForUpdate("theirs").orElseThrow().isValid());
    }
}
