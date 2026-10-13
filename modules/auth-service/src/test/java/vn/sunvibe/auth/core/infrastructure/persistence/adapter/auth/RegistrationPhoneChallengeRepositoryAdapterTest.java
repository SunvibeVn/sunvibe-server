package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationPhoneChallenge;
import vn.sunvibe.common.crypto.AesGcmCryptoService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({RegistrationPhoneChallengeRepositoryAdapter.class, AesGcmCryptoService.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class RegistrationPhoneChallengeRepositoryAdapterTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final String PHONE = "0912345678";

    @Autowired
    private RegistrationPhoneChallengeRepositoryAdapter adapter;

    private RegistrationPhoneChallenge challenge(long id, String phone, Instant createdAt) {
        RegistrationPhoneChallenge challenge = RegistrationPhoneChallenge.issue(id, phone, "123456", createdAt,
                Duration.ofMinutes(10));
        adapter.save(challenge);
        return challenge;
    }

    @Test
    void findLatestForUpdate_returnsTheNewestOfThatPhone() {
        challenge(1L, PHONE, NOW.minusSeconds(120));
        challenge(2L, PHONE, NOW);
        challenge(3L, "0987654321", NOW.plusSeconds(5));

        RegistrationPhoneChallenge latest = adapter.findLatestForUpdate(PHONE).orElseThrow();

        assertEquals(2L, latest.getId());
        assertTrue(latest.matches("123456"), "the stored hash still verifies the code");
        assertEquals(NOW.plus(Duration.ofMinutes(10)), latest.getExpiresAt());
        assertTrue(adapter.findLatestForUpdate("0900000000").isEmpty());
    }

    @Test
    void save_persistsFailedAttemptsVerificationAndRegistration() {
        RegistrationPhoneChallenge challenge = challenge(1L, PHONE, NOW);
        challenge.registerFailedAttempt(5, NOW);
        challenge.verify("the-token", NOW.plusSeconds(1), Duration.ofMinutes(15));
        challenge.markRegistered(NOW.plusSeconds(2));
        adapter.save(challenge);

        RegistrationPhoneChallenge found = adapter.findLatestForUpdate(PHONE).orElseThrow();

        assertEquals(1, found.getFailedAttempts());
        assertEquals(NOW.plusSeconds(1), found.getVerifiedAt());
        assertEquals(RegistrationPhoneChallenge.hashToken("the-token"), found.getTokenHash());
        assertEquals(NOW.plusSeconds(1).plus(Duration.ofMinutes(15)), found.getTokenExpiresAt());
        assertEquals(NOW.plusSeconds(2), found.getRegisteredAt());
    }

    @Test
    void findByTokenHashForUpdate_findsTheChallengeThatIssuedTheToken() {
        RegistrationPhoneChallenge challenge = challenge(1L, PHONE, NOW);
        challenge.verify("the-token", NOW, Duration.ofMinutes(15));
        adapter.save(challenge);
        challenge(2L, "0987654321", NOW);

        assertEquals(1L, adapter.findByTokenHashForUpdate(RegistrationPhoneChallenge.hashToken("the-token"))
                .orElseThrow().getId());
        assertTrue(adapter.findByTokenHashForUpdate(RegistrationPhoneChallenge.hashToken("other")).isEmpty());
    }

    @Test
    void invalidateActive_burnsCodesAndRevokesUnusedTokensOfThatPhoneOnly() {
        challenge(1L, PHONE, NOW.minusSeconds(60));
        RegistrationPhoneChallenge verified = challenge(2L, PHONE, NOW.minusSeconds(30));
        verified.verify("the-token", NOW.minusSeconds(20), Duration.ofMinutes(15));
        adapter.save(verified);
        challenge(3L, "0987654321", NOW);

        adapter.invalidateActive(PHONE, NOW);

        assertFalse(adapter.findLatestForUpdate(PHONE).orElseThrow().isUsable(NOW));
        RegistrationPhoneChallenge revoked = adapter
                .findByTokenHashForUpdate(RegistrationPhoneChallenge.hashToken("the-token")).orElseThrow();
        assertFalse(revoked.isTokenUsable(NOW.plusSeconds(1)));
        assertTrue(adapter.findLatestForUpdate("0987654321").orElseThrow().isUsable(NOW.plusSeconds(1)));
    }

    @Test
    void countIssuedSince_countsOnlyThatPhonesRecentChallenges() {
        challenge(1L, PHONE, NOW.minus(Duration.ofDays(2)));
        challenge(2L, PHONE, NOW.minusSeconds(60));
        challenge(3L, PHONE, NOW);
        challenge(4L, "0987654321", NOW);

        assertEquals(2, adapter.countIssuedSince(PHONE, NOW.minus(Duration.ofDays(1))));
    }

    @Test
    void deleteExpiredBefore_keepsChallengesWhoseTokenIsStillRunning() {
        challenge(1L, PHONE, NOW.minus(Duration.ofDays(3)));
        RegistrationPhoneChallenge withLiveToken = challenge(2L, "0987654321", NOW.minus(Duration.ofDays(3)));
        withLiveToken.verify("the-token", NOW.minus(Duration.ofDays(3)).plusSeconds(1), Duration.ofDays(5));
        adapter.save(withLiveToken);
        challenge(3L, "0900000000", NOW);

        int deleted = adapter.deleteExpiredBefore(NOW.minus(Duration.ofDays(1)));

        assertEquals(1, deleted);
        assertTrue(adapter.findLatestForUpdate(PHONE).isEmpty());
        assertTrue(adapter.findLatestForUpdate("0987654321").isPresent());
        assertTrue(adapter.findLatestForUpdate("0900000000").isPresent());
    }
}
