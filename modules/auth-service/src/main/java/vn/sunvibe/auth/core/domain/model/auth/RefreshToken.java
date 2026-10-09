package vn.sunvibe.auth.core.domain.model.auth;

import lombok.Getter;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One refresh token of a sign-in session.
 * <p>
 * A session (the token <em>family</em>, {@link #familyId}) starts at login and
 * lives until {@link #sessionExpiresAt} at the latest. Every refresh rotates
 * the token: the presented one becomes {@link TokenStatus#USED} and a successor
 * in the same family is issued, valid for the idle lifetime but never beyond
 * the session's absolute expiry. Only a hash of the secret is ever stored.
 */
@Getter
public class RefreshToken {

    private final Long id;
    private final UserId userId;
    private final UUID familyId;
    /** Hex SHA-256 of the secret handed to the client. */
    private final String tokenHash;
    private final Instant issuedAt;
    /** Idle expiry of this token: {@code issuedAt + idle lifetime}, capped by {@link #sessionExpiresAt}. */
    private final Instant expiresAt;
    /** Absolute expiry of the whole session, fixed at login. */
    private final Instant sessionExpiresAt;
    /** Device that obtained this token; informational, never enforced. */
    private final String userAgent;
    private final String ipAddress;
    private TokenStatus status;
    private Instant usedAt;
    private Instant revokedAt;
    private RevokeReason revokeReason;

    private RefreshToken(Long id, UserId userId, UUID familyId, String tokenHash, Instant issuedAt, Instant expiresAt,
                         Instant sessionExpiresAt, String userAgent, String ipAddress, TokenStatus status,
                         Instant usedAt, Instant revokedAt, RevokeReason revokeReason) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("Refresh token hash is required.");
        }
        this.tokenHash = tokenHash;
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.sessionExpiresAt = Objects.requireNonNull(sessionExpiresAt, "sessionExpiresAt");
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.status = Objects.requireNonNull(status, "status");
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
    }

    /**
     * First token of a new sign-in session (login, auto-login after register or
     * password change).
     *
     * @param idleLifetime     how long the token stays valid if not used
     * @param absoluteLifetime how long the session may last at most, however active the user is
     */
    public static RefreshToken startSession(Long id, UserId userId, String tokenHash, Instant now,
                                            Duration idleLifetime, Duration absoluteLifetime,
                                            String userAgent, String ipAddress) {
        Instant sessionExpiresAt = now.plus(absoluteLifetime);
        return new RefreshToken(id, userId, UUID.randomUUID(), tokenHash, now,
                earliest(now.plus(idleLifetime), sessionExpiresAt), sessionExpiresAt,
                userAgent, ipAddress, TokenStatus.ACTIVE, null, null, null);
    }

    /** Rebuilds a token from storage. */
    public static RefreshToken load(Long id, UserId userId, UUID familyId, String tokenHash, Instant issuedAt,
                                    Instant expiresAt, Instant sessionExpiresAt, String userAgent, String ipAddress,
                                    TokenStatus status, Instant usedAt, Instant revokedAt, RevokeReason revokeReason) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, sessionExpiresAt,
                userAgent, ipAddress, status, usedAt, revokedAt, revokeReason);
    }

    /**
     * Exchanges this token for its successor in the same session. The first
     * exchange marks this token {@link TokenStatus#USED}; an exchange tolerated
     * within the reuse grace period keeps the original {@link #usedAt}.
     *
     * @throws IllegalStateException if the token is revoked or expired
     */
    public RefreshToken rotate(Long newId, String newTokenHash, Instant now, Duration idleLifetime,
                               String userAgent, String ipAddress) {
        if (isRevoked() || isExpired(now)) {
            throw new IllegalStateException("A revoked or expired refresh token cannot be rotated.");
        }
        if (status == TokenStatus.ACTIVE) {
            status = TokenStatus.USED;
            usedAt = now;
        }
        return new RefreshToken(newId, userId, familyId, newTokenHash, now,
                earliest(now.plus(idleLifetime), sessionExpiresAt), sessionExpiresAt,
                userAgent, ipAddress, TokenStatus.ACTIVE, null, null, null);
    }

    /** True once the token's idle expiry or the session's absolute expiry has passed. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(sessionExpiresAt);
    }

    public boolean isActive() {
        return status == TokenStatus.ACTIVE;
    }

    public boolean isUsed() {
        return status == TokenStatus.USED;
    }

    public boolean isRevoked() {
        return status == TokenStatus.REVOKED;
    }

    /**
     * A used token presented again shortly after its rotation is a benign race
     * (two tabs refreshing at once, a retried request whose response was lost),
     * not theft: it may be exchanged once more without ending the session.
     */
    public boolean isWithinReuseGrace(Instant now, Duration gracePeriod) {
        return isUsed() && usedAt != null && !now.isAfter(usedAt.plus(gracePeriod));
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

}
