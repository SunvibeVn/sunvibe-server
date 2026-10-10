package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of refresh tokens. Tokens are looked up by the hash of their
 * secret; the secret itself is never stored.
 */
public interface RefreshTokenDomainRepository {

    /** Inserts a new token or updates an existing one. */
    void save(RefreshToken refreshToken);

    /**
     * Finds a token by its hash and locks it until the surrounding transaction
     * ends, so concurrent refreshes of the same token are handled one at a time.
     */
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /** Revokes every not-yet-revoked token of one sign-in session. */
    void revokeFamily(UUID familyId, RevokeReason reason, Instant now);

    /** Revokes every not-yet-revoked token of every session of the user. */
    void revokeAllForUser(UserId userId, RevokeReason reason, Instant now);

    /**
     * Deletes tokens that expired before {@code cutoff}.
     *
     * @return the number of deleted tokens
     */
    int deleteExpiredBefore(Instant cutoff);

}
