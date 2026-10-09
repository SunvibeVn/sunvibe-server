package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenDomainRepository {

    void save(PasswordResetToken passwordResetToken);

    /**
     * Finds a token and locks it until the transaction ends, so concurrent OTP
     * guesses against one token are counted one at a time.
     */
    Optional<PasswordResetToken> findByTokenForUpdate(String token);

    /** The most recently issued token for this e-mail, used or not. */
    Optional<PasswordResetToken> findLatestByEmail(String email);

    /** Burns every still-usable token of this e-mail, so only the newest code works. */
    void invalidateActiveTokens(String email, Instant now);

    void cleanupExpiredTokens();

}
