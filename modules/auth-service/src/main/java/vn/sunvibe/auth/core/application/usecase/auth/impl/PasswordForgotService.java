package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotSmsCommand;
import vn.sunvibe.auth.core.application.query.auth.PasswordForgotResult;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordForgotUseCase;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Step 1 of the password reset: sends an OTP by e-mail or SMS.
 * <p>
 * The answer has the same shape whether or not the e-mail/phone belongs to an
 * account, so this endpoint cannot tell who is registered: whenever no code is
 * sent (unknown or ineligible account, cooldown, daily limit) the answer
 * carries a fresh random decoy token and a fresh expiry, exactly like a newly
 * sent code. Only a <em>verified</em> e-mail may be used. Codes are not re-sent
 * within the cooldown, at most {@code maxCodesPerDay} are sent per destination
 * per 24 h, and a new code burns the previous ones.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class PasswordForgotService implements PasswordForgotUseCase {

    public static final String GENERIC_MESSAGE = "If an account exists for this email, a password reset code has been sent.";
    public static final String GENERIC_SMS_MESSAGE = "If an account exists for this phone number, a password reset code has been sent by SMS.";

    private final UserDomainRepository userDomainRepository;
    private final PasswordResetTokenDomainRepository passwordResetTokenDomainRepository;
    private final EmailServicePort emailService;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final PasswordResetPolicy passwordResetPolicy;
    private final AuditLogPort auditLog;
    private final SmsSenderPort smsSender;

    @Override
    @Transactional
    public PasswordForgotResult execute(PasswordForgotCommand command) {
        // Stored e-mails are lower-case (see Email VO)
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        // An unverified e-mail proves nothing: whoever typed it at sign-up may not own it
        Optional<AppUser> account = find(() -> userDomainRepository.findByEmail(email))
                .filter(user -> user.canAuthenticate() && user.isEmailVerified());

        Optional<PasswordResetToken> issued = issue(VerificationChannel.EMAIL, email, account,
                (token, otp) -> emailService.sendPasswordResetEmail(email, otp, token.getToken()));

        // The e-mail flow never returns the token: it travels in the e-mail
        return response(GENERIC_MESSAGE, null, issued);
    }

    @Override
    @Transactional
    public PasswordForgotResult executeBySms(PasswordForgotSmsCommand command) {
        String phone = command.phoneNumber().trim();
        Optional<AppUser> account = find(() -> userDomainRepository.findByUsername(phone)).filter(AppUser::canAuthenticate);

        Optional<PasswordResetToken> issued = issue(VerificationChannel.PHONE, phone, account,
                (token, otp) -> smsSender.send(phone, "Ma dat lai mat khau Sunvibe cua ban la " + otp
                        + ". Ma het han sau " + PasswordResetToken.LIFETIME.toMinutes()
                        + " phut. Khong chia se ma nay cho bat ky ai."));

        // A random token when nothing was issued keeps the answer indistinguishable
        String token = issued.map(PasswordResetToken::getToken).orElseGet(() -> UUID.randomUUID().toString());
        return response(GENERIC_SMS_MESSAGE, token, issued);
    }

    /**
     * Issues and delivers a new code when allowed.
     * <p>
     * Within the cooldown the code sent moments ago is <em>not</em> handed out
     * again: returning the same token twice would reveal that the destination
     * belongs to an account. The user keeps the token of the first answer.
     *
     * @return the new token, empty when nothing was sent
     */
    private Optional<PasswordResetToken> issue(VerificationChannel channel, String destination, Optional<AppUser> account,
                                               Delivery delivery) {
        Instant now = AppClock.currentUTC();
        if (account.isEmpty()) {
            log.info("Password reset by {} requested for an unknown or ineligible account; nothing sent", channel);
            audit(null, destination, channel, "NO_ELIGIBLE_ACCOUNT");
            return Optional.empty();
        }
        AppUser user = account.get();
        String userId = user.getId().getValue();

        Optional<PasswordResetToken> latest = passwordResetTokenDomainRepository.findLatestByDestination(destination);
        if (latest.isPresent() && latest.get().isValid()
                && now.isBefore(latest.get().getCreatedAt().plus(passwordResetPolicy.getResendCooldown()))) {
            log.info("Password reset code for user {} not re-sent: requested again within the cooldown", userId);
            audit(userId, destination, channel, "COOLDOWN_NOT_SENT");
            return Optional.empty();
        }
        if (passwordResetTokenDomainRepository.countIssuedSince(destination, now.minus(Duration.ofDays(1)))
                >= passwordResetPolicy.getMaxCodesPerDay()) {
            log.warn("Password reset code for user {} not sent: daily limit reached", userId);
            audit(userId, destination, channel, "DAILY_LIMIT");
            return Optional.empty();
        }

        // Only the newest code may work
        passwordResetTokenDomainRepository.invalidateActiveTokens(destination, now);

        OtpCode otpCode = OtpCode.create();
        PasswordResetToken token = PasswordResetToken.create(snowflakeIdGenerator.generateId(), userId, channel,
                destination, otpCode.getCode());
        passwordResetTokenDomainRepository.save(token);
        delivery.send(token, otpCode.getCode());

        log.info("Password reset code sent by {} for user {}", channel, userId);
        audit(userId, destination, channel, "SENT");
        return Optional.of(token);
    }

    private static PasswordForgotResult response(String message, String resetToken, Optional<PasswordResetToken> issued) {
        Instant expiresAt = issued.map(PasswordResetToken::getExpiresAt)
                .orElseGet(() -> AppClock.currentUTC().plus(PasswordResetToken.LIFETIME));
        return new PasswordForgotResult(message, resetToken, expiresAt, PasswordResetToken.LIFETIME);
    }

    private void audit(String userId, String destination, VerificationChannel channel, String outcome) {
        auditLog.record(AuthAuditEvent.of(AuditEventType.PASSWORD_RESET_REQUESTED, userId, destination,
                channel + " " + outcome));
    }

    private static Optional<AppUser> find(Supplier<AppUser> lookup) {
        try {
            return Optional.of(lookup.get());
        } catch (UserNotFoundException e) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface Delivery {
        void send(PasswordResetToken token, String otp);
    }
}
