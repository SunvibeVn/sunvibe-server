package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.common.crypto.AesGcmCryptoService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({VerificationCodeRepositoryAdapter.class, AesGcmCryptoService.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class VerificationCodeRepositoryAdapterTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private VerificationCodeRepositoryAdapter adapter;

    private VerificationCode code(long id, String userId, VerificationChannel channel, Instant createdAt) {
        VerificationCode code = VerificationCode.issue(id, userId, channel, "0912345678", "123456", createdAt,
                Duration.ofMinutes(10));
        adapter.save(code);
        return code;
    }

    @Test
    void findLatestForUpdate_returnsTheNewestOfThatUserAndChannel() {
        code(1L, "user-1", VerificationChannel.PHONE, NOW.minusSeconds(120));
        code(2L, "user-1", VerificationChannel.PHONE, NOW);
        code(3L, "user-1", VerificationChannel.EMAIL, NOW.plusSeconds(5));
        code(4L, "user-2", VerificationChannel.PHONE, NOW.plusSeconds(5));

        VerificationCode latest = adapter.findLatestForUpdate("user-1", VerificationChannel.PHONE).orElseThrow();

        assertEquals(2L, latest.getId());
        assertTrue(latest.matches("123456"), "the stored hash still verifies the code");
        assertEquals(NOW.plus(Duration.ofMinutes(10)), latest.getExpiresAt());
        assertTrue(adapter.findLatestForUpdate("user-3", VerificationChannel.PHONE).isEmpty());
    }

    @Test
    void save_persistsFailedAttemptsAndConsumption() {
        VerificationCode code = code(1L, "user-1", VerificationChannel.PHONE, NOW);
        code.registerFailedAttempt(5, NOW);
        code.consume(NOW.plusSeconds(1));
        adapter.save(code);

        VerificationCode found = adapter.findLatestForUpdate("user-1", VerificationChannel.PHONE).orElseThrow();

        assertEquals(1, found.getFailedAttempts());
        assertEquals(NOW.plusSeconds(1), found.getConsumedAt());
    }

    @Test
    void invalidateActive_burnsOnlyThatUsersChannel() {
        code(1L, "user-1", VerificationChannel.PHONE, NOW);
        code(2L, "user-1", VerificationChannel.EMAIL, NOW);

        adapter.invalidateActive("user-1", VerificationChannel.PHONE, NOW);

        assertFalse(adapter.findLatestForUpdate("user-1", VerificationChannel.PHONE).orElseThrow().isUsable(NOW));
        assertTrue(adapter.findLatestForUpdate("user-1", VerificationChannel.EMAIL).orElseThrow().isUsable(NOW));
    }

    @Test
    void countIssuedSince_countsWithinTheWindow() {
        code(1L, "user-1", VerificationChannel.PHONE, NOW.minus(Duration.ofHours(30)));
        code(2L, "user-1", VerificationChannel.PHONE, NOW.minus(Duration.ofHours(2)));
        code(3L, "user-1", VerificationChannel.PHONE, NOW);

        assertEquals(2, adapter.countIssuedSince("user-1", VerificationChannel.PHONE, NOW.minus(Duration.ofDays(1))));
    }
}
