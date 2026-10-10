package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.manual.RefreshTokenEntityMapper;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.RefreshTokenJpaRepository;
import vn.sunvibe.common.crypto.AesGcmCryptoService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({RefreshTokenRepositoryAdapter.class, RefreshTokenEntityMapper.class, AesGcmCryptoService.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class RefreshTokenRepositoryAdapterTest {

    private static final UserId ALICE = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    private static final UserId BOB = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FBB");
    // Microsecond precision, like the database columns
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private RefreshTokenRepositoryAdapter adapter;

    @Autowired
    private RefreshTokenJpaRepository jpaRepository;

    private RefreshToken session(long id, UserId userId, String hash) {
        RefreshToken token = RefreshToken.startSession(id, userId, hash, NOW, Duration.ofDays(7), Duration.ofDays(30),
                "Mozilla/5.0", "203.0.113.7");
        adapter.save(token);
        return token;
    }

    private TokenStatus statusOf(long id) {
        return jpaRepository.findById(id).orElseThrow().getStatus();
    }

    @Test
    void save_thenFindByHash_roundTripsEveryField() {
        RefreshToken saved = session(1L, ALICE, "hash-1");

        RefreshToken found = adapter.findByTokenHashForUpdate("hash-1").orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals(ALICE, found.getUserId());
        assertEquals(saved.getFamilyId(), found.getFamilyId());
        assertEquals(saved.getExpiresAt(), found.getExpiresAt());
        assertEquals(saved.getSessionExpiresAt(), found.getSessionExpiresAt());
        assertEquals("Mozilla/5.0", found.getUserAgent());
        assertEquals("203.0.113.7", found.getIpAddress());
        assertTrue(found.isActive());
        assertTrue(adapter.findByTokenHashForUpdate("other-hash").isEmpty());
    }

    @Test
    void save_persistsRotation() {
        RefreshToken token = session(1L, ALICE, "hash-1");
        RefreshToken next = token.rotate(2L, "hash-2", NOW.plusSeconds(60), Duration.ofDays(7), null, null);

        adapter.save(token);
        adapter.save(next);

        assertEquals(TokenStatus.USED, statusOf(1L));
        assertEquals(TokenStatus.ACTIVE, statusOf(2L));
        assertEquals(token.getFamilyId(), adapter.findByTokenHashForUpdate("hash-2").orElseThrow().getFamilyId());
    }

    @Test
    void save_rejectsDuplicateTokenHash() {
        session(1L, ALICE, "same-hash");

        assertThrows(DataIntegrityViolationException.class, () -> {
            session(2L, BOB, "same-hash");
            jpaRepository.flush();
        });
    }

    @Test
    void revokeFamily_revokesOnlyThatSession() {
        RefreshToken token = session(1L, ALICE, "hash-1");
        adapter.save(token.rotate(2L, "hash-2", NOW.plusSeconds(60), Duration.ofDays(7), null, null));
        session(3L, ALICE, "hash-3");

        adapter.revokeFamily(token.getFamilyId(), RevokeReason.REUSE_DETECTED, NOW);

        RefreshToken revoked = adapter.findByTokenHashForUpdate("hash-2").orElseThrow();
        assertTrue(revoked.isRevoked());
        assertEquals(RevokeReason.REUSE_DETECTED, revoked.getRevokeReason());
        assertEquals(NOW, revoked.getRevokedAt());
        assertEquals(TokenStatus.REVOKED, statusOf(1L));
        assertEquals(TokenStatus.ACTIVE, statusOf(3L));
    }

    @Test
    void revokeFamily_keepsTheOriginalReasonOfAlreadyRevokedTokens() {
        RefreshToken token = session(1L, ALICE, "hash-1");
        adapter.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT, NOW);

        adapter.revokeFamily(token.getFamilyId(), RevokeReason.REUSE_DETECTED, NOW.plusSeconds(5));

        RefreshToken found = adapter.findByTokenHashForUpdate("hash-1").orElseThrow();
        assertEquals(RevokeReason.LOGOUT, found.getRevokeReason());
        assertEquals(NOW, found.getRevokedAt());
    }

    @Test
    void revokeAllForUser_revokesEverySessionOfThatUserOnly() {
        session(1L, ALICE, "hash-1");
        session(2L, ALICE, "hash-2");
        session(3L, BOB, "hash-3");

        adapter.revokeAllForUser(ALICE, RevokeReason.PASSWORD_CHANGED, NOW);

        assertEquals(TokenStatus.REVOKED, statusOf(1L));
        assertEquals(TokenStatus.REVOKED, statusOf(2L));
        assertEquals(TokenStatus.ACTIVE, statusOf(3L));
    }

    @Test
    void deleteExpiredBefore_deletesOnlyTokensExpiredBeforeCutoff() {
        adapter.save(RefreshToken.load(1L, ALICE, UUID.randomUUID(), "old", NOW.minus(Duration.ofDays(10)),
                NOW.minus(Duration.ofDays(3)), NOW.plus(Duration.ofDays(20)), null, null, TokenStatus.USED,
                NOW.minus(Duration.ofDays(9)), null, null));
        session(2L, ALICE, "fresh");

        int deleted = adapter.deleteExpiredBefore(NOW.minus(Duration.ofDays(1)));

        assertEquals(1, deleted);
        assertTrue(jpaRepository.findById(1L).isEmpty());
        assertTrue(jpaRepository.findById(2L).isPresent());
    }
}
