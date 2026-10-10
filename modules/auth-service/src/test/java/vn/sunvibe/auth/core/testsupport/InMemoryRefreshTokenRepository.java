package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-written fake (Mockito is not on this module's classpath). Stores copies
 * via {@link RefreshToken#load}, like a database would, so a test only sees
 * changes that were explicitly saved.
 */
public class InMemoryRefreshTokenRepository implements RefreshTokenDomainRepository {

    private final Map<Long, RefreshToken> tokens = new LinkedHashMap<>();
    /** Every revokeFamily/revokeAllForUser call, as "family:&lt;id&gt;:REASON" / "user:&lt;id&gt;:REASON". */
    public final List<String> revocations = new ArrayList<>();

    @Override
    public void save(RefreshToken token) {
        tokens.put(token.getId(), copy(token, token.getStatus(), token.getRevokedAt(), token.getRevokeReason()));
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return tokens.values().stream()
                .filter(token -> token.getTokenHash().equals(tokenHash))
                .findFirst()
                .map(token -> copy(token, token.getStatus(), token.getRevokedAt(), token.getRevokeReason()));
    }

    @Override
    public void revokeFamily(UUID familyId, RevokeReason reason, Instant now) {
        revocations.add("family:" + familyId + ":" + reason);
        tokens.replaceAll((id, token) -> token.getFamilyId().equals(familyId) && !token.isRevoked()
                ? copy(token, TokenStatus.REVOKED, now, reason) : token);
    }

    @Override
    public void revokeAllForUser(UserId userId, RevokeReason reason, Instant now) {
        revocations.add("user:" + userId.getValue() + ":" + reason);
        tokens.replaceAll((id, token) -> token.getUserId().equals(userId) && !token.isRevoked()
                ? copy(token, TokenStatus.REVOKED, now, reason) : token);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        int before = tokens.size();
        tokens.values().removeIf(token -> token.getExpiresAt().isBefore(cutoff));
        return before - tokens.size();
    }

    public List<RefreshToken> all() {
        return List.copyOf(tokens.values());
    }

    public RefreshToken get(Long id) {
        return tokens.get(id);
    }

    private static RefreshToken copy(RefreshToken token, TokenStatus status, Instant revokedAt, RevokeReason reason) {
        return RefreshToken.load(token.getId(), token.getUserId(), token.getFamilyId(), token.getTokenHash(),
                token.getIssuedAt(), token.getExpiresAt(), token.getSessionExpiresAt(), token.getUserAgent(),
                token.getIpAddress(), status, token.getUsedAt(), revokedAt, reason);
    }
}
