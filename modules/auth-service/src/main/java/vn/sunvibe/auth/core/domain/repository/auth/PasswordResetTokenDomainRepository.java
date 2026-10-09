package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenDomainRepository {

    void save(PasswordResetToken passwordResetToken);

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByEmailAndNotUsed(String email);

    void markAsUsed(String token);

    void cleanupExpiredTokens();

}
