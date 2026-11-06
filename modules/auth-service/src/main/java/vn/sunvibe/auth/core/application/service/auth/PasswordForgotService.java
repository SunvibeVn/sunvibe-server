package vn.sunvibe.auth.core.application.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotSmsCommand;
import vn.sunvibe.auth.core.application.query.auth.PasswordForgotResult;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.shared.OtpHasher;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy;
import vn.sunvibe.auth.core.application.shared.RecoveryMailDispatcher;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordForgotUseCase;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.usecase.external.lock.AuthAdvisoryLockPort;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Step 1 of the password reset: sends an OTP by e-mail or SMS.
 * <p>
 * The answer has the same shape whether or not the e-mail/phone belongs to an
 * account, so this endpoint cannot tell who is registered: whenever no code is
 * sent (unknown or ineligible account, cooldown, daily limit, a parallel request)
 * the answer carries a fresh random decoy token and a fresh expiry, exactly like a
 * newly sent code. Only a <em>verified</em> e-mail may be used. Codes are not
 * re-sent within the cooldown, at most {@code maxCodesPerDay} are sent per
 * destination per 24 h, and a new code burns the previous ones. The mail leaves
 * after the commit, so a failed send cannot show in the answer either.
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
    private final PhoneChannelPolicy phoneChannel;
    private final AuthAdvisoryLockPort advisoryLock;
    private final OtpHasher otpHasher;
    private final RecoveryMailDispatcher recoveryMail;

    @Override
    @Transactional
    public PasswordForgotResult execute(PasswordForgotCommand command) {
        String emailKey = Email.create(command.email()).getValue();
        // An unverified e-mail proves nothing: whoever typed it at sign-up may not own it
        Optional<AppUser> account = userDomainRepository.findByEmailKey(emailKey)
                .filter(user -> user.canAuthenticate() && user.isEmailVerified());

        Optional<PasswordResetToken.Issued> issued = issue(VerificationChannel.EMAIL, emailKey, account,
                (userId, token, otp) -> recoveryMail.dispatch(AuditEventType.PASSWORD_RESET_REQUESTED, userId, () -> {
                    emailService.sendPasswordResetEmail(emailKey, otp, token);
                    audit(userId, emailKey, VerificationChannel.EMAIL, "SENT");
                }));

        // The e-mail flow never returns the token: it travels in the e-mail
        return response(GENERIC_MESSAGE, null, issued);
    }

    @Override
    @Transactional
    public PasswordForgotResult executeBySms(PasswordForgotSmsCommand command) {
        phoneChannel.require(PhoneChannelPolicy.Operation.SMS_RESET);
        String phone = PhoneNumber.create(command.phoneNumber()).key();
        // auth-email-access D4-9: a verified email with an unproven phone gets no SMS reset
        Optional<AppUser> account = userDomainRepository.findByPhoneKey(phone)
                .filter(AppUser::canAuthenticate)
                .filter(AppUser::canUsePhone);

        Optional<PasswordResetToken.Issued> issued = issue(VerificationChannel.PHONE, phone, account, (userId, token, otp) -> {
            smsSender.send(phone, "Ma dat lai mat khau Sunvibe cua ban la " + otp
                    + ". Ma het han sau " + PasswordResetToken.LIFETIME.toMinutes()
                    + " phut. Khong chia se ma nay cho bat ky ai.");
            audit(userId, phone, VerificationChannel.PHONE, "SENT");
        });

        // A random token when nothing was issued keeps the answer indistinguishable
        String token = issued.map(PasswordResetToken.Issued::plainToken).orElseGet(() -> UUID.randomUUID().toString());
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
    private Optional<PasswordResetToken.Issued> issue(VerificationChannel channel, String destination,
                                                      Optional<AppUser> account, Delivery delivery) {
        Instant now = AppClock.currentUTC();
        if (account.isEmpty()) {
            log.info("Password reset by {} requested for an unknown or ineligible account; nothing sent", channel);
            audit(null, destination, channel, "NO_ELIGIBLE_ACCOUNT");
            return Optional.empty();
        }
        AppUser user = account.get();
        String userId = user.getId().getValue();

        // Before the throttle, or parallel requests would all pass it; a miss is as neutral as the rest
        if (!advisoryLock.tryLock("reset:" + destination)) {
            log.info("Password reset code for user {} not sent: a parallel request holds the destination", userId);
            audit(userId, destination, channel, "LOCK_MISS");
            return Optional.empty();
        }

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

        String otp = OtpCode.create().getCode();
        Long id = snowflakeIdGenerator.generateId();
        PasswordResetToken.Issued issued = PasswordResetToken.issue(id, userId, channel, destination, otpHasher.hash(id, otp),
                user.getCredentialVersion());
        passwordResetTokenDomainRepository.save(issued.token());
        delivery.send(userId, issued.plainToken(), otp);

        log.info("Password reset code issued by {} for user {}", channel, userId);
        return Optional.of(issued);
    }

    private static PasswordForgotResult response(String message, String resetToken, Optional<PasswordResetToken.Issued> issued) {
        Instant expiresAt = issued.map(i -> i.token().getExpiresAt())
                .orElseGet(() -> AppClock.currentUTC().plus(PasswordResetToken.LIFETIME));
        return new PasswordForgotResult(message, resetToken, expiresAt, PasswordResetToken.LIFETIME);
    }

    private void audit(String userId, String destination, VerificationChannel channel, String outcome) {
        String masked = channel == VerificationChannel.EMAIL
                ? ContactMasker.maskEmail(destination) : ContactMasker.maskPhone(destination);
        auditLog.record(AuthAuditEvent.of(AuditEventType.PASSWORD_RESET_REQUESTED, userId, masked, channel + " " + outcome));
    }

    @FunctionalInterface
    private interface Delivery {
        void send(String userId, String plainToken, String otp);
    }
}
