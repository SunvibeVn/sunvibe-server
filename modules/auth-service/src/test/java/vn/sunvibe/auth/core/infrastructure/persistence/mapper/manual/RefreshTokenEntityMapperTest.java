package vn.sunvibe.auth.core.infrastructure.persistence.mapper.manual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

/**
 * A stored refresh token keeps every value in its own field through the row and back (cleanup spec A-5, SV-169):
 * each parameter of {@link RefreshToken#load} gets a value no other parameter has, so two arguments swapped in the
 * mapper or in {@code load} fail here.
 */
class RefreshTokenEntityMapperTest {

    private static final UUID FAMILY = UUID.fromString("3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61");

    private final RefreshTokenEntityMapper mapper = new RefreshTokenEntityMapper();

    @Test
    void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
        RefreshToken stored = RefreshToken.load(101L, UserId.from("user-1"), FAMILY, "hash-1", at(1), at(2), at(3),
                "agent-1", "10.0.0.1", TokenStatus.REVOKED, at(4), at(5), RevokeReason.LOGOUT_ALL);

        RefreshToken back = mapper.toDomain(mapper.toEntity(stored));

        assertEquals(101L, back.getId());
        assertEquals("user-1", back.getUserId().getValue());
        assertEquals(FAMILY, back.getFamilyId());
        assertEquals("hash-1", back.getTokenHash());
        assertEquals(at(1), back.getIssuedAt());
        assertEquals(at(2), back.getExpiresAt());
        assertEquals(at(3), back.getSessionExpiresAt());
        assertEquals("agent-1", back.getUserAgent());
        assertEquals("10.0.0.1", back.getIpAddress());
        assertEquals(TokenStatus.REVOKED, back.getStatus());
        assertEquals(at(4), back.getUsedAt());
        assertEquals(at(5), back.getRevokedAt());
        assertEquals(RevokeReason.LOGOUT_ALL, back.getRevokeReason());
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-11-01T01:00:00Z").plusSeconds(60L * minute);
    }
}
