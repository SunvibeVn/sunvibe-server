package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;

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
    Optional<PasswordResetToken> findByTokenForUpdate(String token);

    /** The most recently issued token for this e-mail or phone number, used or not. */
    Optional<PasswordResetToken> findLatestByDestination(String destination);

    /** Burns every still-usable token of this destination, so only the newest code works. */
    void invalidateActiveTokens(String destination, Instant now);

    /** Tokens issued to this destination since {@code since} (daily cap). */
    long countIssuedSince(String destination, Instant since);

}
