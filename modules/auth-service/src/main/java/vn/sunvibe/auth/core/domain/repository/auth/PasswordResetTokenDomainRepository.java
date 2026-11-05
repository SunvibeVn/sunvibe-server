package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence of password-reset tokens.
 */
public interface PasswordResetTokenDomainRepository {

    /** Inserts a new token or updates an existing one. */
    void save(PasswordResetToken passwordResetToken);

    /**
     * Finds a token and locks it until the transaction ends, so concurrent OTP
     * guesses against one token are counted one at a time.
     */
    Optional<PasswordResetToken> findByTokenForUpdate(String tokenHash);

    /** The owner of a token, as a plain value, for the user lock that comes first (auth-email-access spec 5.4). */
    Optional<UserId> findUserIdByToken(String tokenHash);

    /** The most recently issued token for this e-mail or phone number, used or not. */
    Optional<PasswordResetToken> findLatestByDestination(String destination);

    /** Burns every still-usable token of this destination, so only the newest code works. */
    void invalidateActiveTokens(String destination, Instant now);

    /** Tokens issued to this destination since {@code since} (daily cap). */
    long countIssuedSince(String destination, Instant since);

    /**
     * Deletes up to {@code limit} rows whose last expiry is before {@code cutoff}.
     *
     * @return the number of deleted rows
     */
    int deleteExpiredBatch(Instant cutoff, int limit);

}
