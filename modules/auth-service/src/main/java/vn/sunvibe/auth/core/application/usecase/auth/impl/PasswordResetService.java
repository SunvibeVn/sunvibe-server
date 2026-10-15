package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.PasswordResetCommand;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordResetUseCase;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.util.Optional;

/**
 * Step 2 of the password reset (e-mail or SMS): checks the OTP and sets the new password.
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
    private final SmsSenderPort smsSender;

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class, PasswordResetTokenException.class})
    public Instant verifyOtpAndResetPassword(PasswordResetCommand command) {
        Instant now = AppClock.currentUTC();

        // Find reset token (locked: concurrent guesses are counted one at a time)
        PasswordResetToken passwordResetToken = passwordResetTokenDomainRepository.findByTokenForUpdate(command.resetToken())
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
        if (!passwordResetToken.matchesOtp(command.otpCode())) {
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

        // Find user (the token, not the destination, identifies the account)
        Optional<AppUser> owner = userDomainRepository.findById(UserId.from(passwordResetToken.getUserId()));
        if (owner.isEmpty()) {
            audit(AuditEventType.PASSWORD_RESET_FAILED, passwordResetToken, "ACCOUNT_GONE");
            throw new PasswordResetTokenException(INVALID_RESET_CODE);
        }
        AppUser user = owner.get();

        // A locked/disabled account must not regain access through the reset flow
        if (!user.canAuthenticate()) {
            audit(AuditEventType.PASSWORD_RESET_FAILED, passwordResetToken, "ACCOUNT_DISABLED");
            throw new AuthenticationFailedException("Account is locked or disabled.");
        }

        // Change password; proving control of the e-mail/phone also lifts a login lockout
        user.changePassword(passwordEncoder.encode(command.newPassword()));
        user.clearLoginLockout();
        // An SMS code reached this phone: its owner is verified (and takes back a number someone else registered)
        boolean phoneNewlyVerified = passwordResetToken.getChannel() == VerificationChannel.PHONE && !user.isPhoneVerified();
        if (passwordResetToken.getChannel() == VerificationChannel.PHONE) {
            user.verifyPhone(now);
        }

        // Save user
        userDomainRepository.save(user);

        // Mark reset token as used
        passwordResetToken.markAsUsed(now);
        passwordResetTokenDomainRepository.save(passwordResetToken);

        // Whoever knew the old password may hold a session: end all of them.
        // No new session is started here; the user signs in with the new password.
        refreshTokenRepository.revokeAllForUser(user.getId(), RevokeReason.PASSWORD_RESET, now);

        // Tell the owner on the channel that was used
        if (passwordResetToken.getChannel() == VerificationChannel.PHONE) {
            notifyBySms(user.getUsername().getValue());
        } else {
            emailService.sendPasswordResetConfirmationEmail(passwordResetToken.getDestination());
        }
        if (phoneNewlyVerified) {
            audit(AuditEventType.PHONE_VERIFIED, passwordResetToken, "BY_SMS_PASSWORD_RESET");
        }

        log.info("Password reset successfully for user: {}", user.getId().getValue());
        audit(AuditEventType.PASSWORD_RESET_COMPLETED, passwordResetToken, null);

        return now;
    }

    private void audit(AuditEventType type, PasswordResetToken token, String details) {
        auditLog.record(AuthAuditEvent.of(type, token.getUserId(), token.getDestination(), details));
    }

    private void notifyBySms(String phone) {
        try {
            smsSender.send(phone, "Mat khau tai khoan Sunvibe cua ban vua duoc dat lai. "
                    + "Neu khong phai ban, hay lien he ho tro ngay.");
        } catch (RuntimeException e) {
            log.warn("Could not send password-changed SMS: {}", e.getMessage());
        }
    }
}
