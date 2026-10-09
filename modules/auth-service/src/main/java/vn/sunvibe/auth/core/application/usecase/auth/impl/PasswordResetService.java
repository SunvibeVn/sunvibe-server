package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordResetUseCase;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordResetResponse;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Step 2 of the e-mail password reset: checks the OTP and sets the new password.
 * <p>
 * Each reset token tolerates {@link PasswordResetPolicy#getMaxOtpAttempts()}
 * wrong codes and is then burned. The failed-attempt count is committed even
 * though the request fails ({@code noRollbackFor}), otherwise guesses would be free.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class PasswordResetService implements PasswordResetUseCase {

    static final String INVALID_RESET_CODE = "The reset code is invalid or has expired. Please request a new one.";
    static final String TOO_MANY_ATTEMPTS = "Too many incorrect codes. Please request a new one.";

    private final PasswordResetTokenDomainRepository passwordResetTokenDomainRepository;
    private final UserDomainRepository userDomainRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailServicePort emailService;
    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final PasswordResetPolicy passwordResetPolicy;
    private final AuditLogPort auditLog;

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class, PasswordResetTokenException.class})
    public PasswordResetResponse verifyOtpAndResetPassword(PasswordResetRequest request) {
        Instant now = AppClock.currentUTC();

        // Find reset token (locked: concurrent guesses are counted one at a time)
        PasswordResetToken passwordResetToken = passwordResetTokenDomainRepository.findByTokenForUpdate(request.getResetToken())
                .orElseThrow(() -> {
                    auditLog.record(AuthAuditEvent.of(AuditEventType.PASSWORD_RESET_FAILED, null, null, "UNKNOWN_TOKEN"));
                    return new PasswordResetTokenException(INVALID_RESET_CODE);
                });

        // Check token validity
        if (!passwordResetToken.isValid()) {
            audit(AuditEventType.PASSWORD_RESET_FAILED, passwordResetToken, "TOKEN_EXPIRED_OR_USED");
            throw new PasswordResetTokenException(INVALID_RESET_CODE);
        }

        // Validate OTP; a wrong code counts against this token
        if (!passwordResetToken.matchesOtp(request.getOtpCode())) {
            boolean burned = passwordResetToken.registerFailedAttempt(passwordResetPolicy.getMaxOtpAttempts(), now);
            passwordResetTokenDomainRepository.save(passwordResetToken);
            if (burned) {
                log.warn("Password reset token for user {} burned after {} wrong OTP codes",
                        passwordResetToken.getUserId(), passwordResetToken.getFailedAttempts());
                audit(AuditEventType.PASSWORD_RESET_FAILED, passwordResetToken, "TOKEN_BURNED_AFTER_WRONG_OTP");
                throw new PasswordResetTokenException(TOO_MANY_ATTEMPTS);
            }
            audit(AuditEventType.PASSWORD_RESET_FAILED, passwordResetToken,
                    "WRONG_OTP attempt=" + passwordResetToken.getFailedAttempts());
            throw new InvalidOtpException("Invalid OTP code");
        }

        // Find user
        AppUser user = userDomainRepository.findByEmail(passwordResetToken.getEmail());

        // A locked/disabled account must not regain access through the reset flow
        if (!user.canAuthenticate()) {
            audit(AuditEventType.PASSWORD_RESET_FAILED, passwordResetToken, "ACCOUNT_DISABLED");
            throw new AuthenticationFailedException("Account is locked or disabled.");
        }

        // Change password; proving control of the e-mail also lifts a login lockout
        String encryptedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(Password.create(encryptedPassword));
        user.clearLoginLockout();

        // Save user
        userDomainRepository.save(user);

        // Mark reset token as used
        passwordResetToken.markAsUsed(now);
        passwordResetTokenDomainRepository.save(passwordResetToken);

        // Whoever knew the old password may hold a session: end all of them.
        // No new session is started here; the user signs in with the new password.
        refreshTokenRepository.revokeAllForUser(user.getId(), RevokeReason.PASSWORD_RESET, now);

        // Send confirmation email
        emailService.sendPasswordResetConfirmationEmail(user.getEmail().getValue());

        log.info("Password reset successfully for user: {}", user.getId().getValue());
        audit(AuditEventType.PASSWORD_RESET_COMPLETED, passwordResetToken, null);

        return PasswordResetResponse.builder()
                .message("Password reset successfully")
                .passwordChangedAt(LocalDateTime.now())
                .build();
    }

    private void audit(AuditEventType type, PasswordResetToken token, String details) {
        auditLog.record(AuthAuditEvent.of(type, token.getUserId(), token.getEmail(), details));
    }
}
