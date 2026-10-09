package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written fake. Stores copies, like a database, so a test only sees
 * changes that were explicitly saved.
 */
public class InMemoryPasswordResetTokenRepository implements PasswordResetTokenDomainRepository {

    private final Map<Long, PasswordResetToken> tokens = new LinkedHashMap<>();

    @Override
    public void save(PasswordResetToken token) {
        tokens.put(token.getId(), copy(token, token.isUsed(), token.getUsedAt()));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenForUpdate(String token) {
        return tokens.values().stream().filter(t -> t.getToken().equals(token)).findFirst()
                .map(t -> copy(t, t.isUsed(), t.getUsedAt()));
    }

    @Override
    public Optional<PasswordResetToken> findLatestByEmail(String email) {
        return tokens.values().stream().filter(t -> t.getEmail().equals(email))
                .max(Comparator.comparing(PasswordResetToken::getCreatedAt))
                .map(t -> copy(t, t.isUsed(), t.getUsedAt()));
    }

    @Override
    public void invalidateActiveTokens(String email, Instant now) {
        tokens.replaceAll((id, t) -> t.getEmail().equals(email) && !t.isUsed() ? copy(t, true, now) : t);
    }

    @Override
    public void cleanupExpiredTokens() {
        throw new UnsupportedOperationException();
    }

    public PasswordResetToken get(Long id) {
        return tokens.get(id);
    }

    public List<PasswordResetToken> all() {
        return List.copyOf(tokens.values());
    }

    private static PasswordResetToken copy(PasswordResetToken t, boolean used, Instant usedAt) {
        return PasswordResetToken.restore(t.getId(), t.getToken(), t.getUserId(), t.getEmail(), t.getCreatedAt(),
                t.getExpiresAt(), used, usedAt, t.getFailedAttempts(), t.getOtpCode());
    }
}
