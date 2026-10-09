package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.shared.CurrentUser;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.ContactVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;
import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.repository.auth.VerificationCodeRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Phone (SMS) and e-mail verification codes for the signed-in user.
 * <p>
 * One usable code per account and channel: a new code burns the previous one.
 * Codes are throttled (cooldown + daily cap), tolerate a few wrong guesses and
 * are bound to the address they were sent to. Failed guesses are committed
 * even though the request fails ({@code noRollbackFor}).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ContactVerificationService implements ContactVerificationUseCase {

    static final String ALREADY_VERIFIED = "This contact is already verified.";
    static final String NO_EMAIL = "There is no email on this account to verify.";
    static final String INVALID_CODE = "The verification code is invalid or has expired. Please request a new one.";
    static final String WRONG_CODE = "Incorrect verification code.";
    static final String TOO_MANY_ATTEMPTS = "Too many incorrect codes. Please request a new one.";
    static final String TOO_SOON = "Please wait before requesting another code.";
    static final String DAILY_LIMIT = "Too many codes requested today. Please try again later.";

    private final UserDomainRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final SmsSenderPort smsSender;
    private final EmailServicePort emailService;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final VerificationPolicy policy;
    private final AuditLogPort auditLog;

    @Override
    @Transactional
    public VerificationSendResult sendCode(VerificationChannel channel) {
        return send(currentUser(), channel, AppClock.currentUTC());
    }

    @Override
    @Transactional
    public void sendCodeAfterRegistration(AppUser user, VerificationChannel channel) {
        // Exceptions must not leave this method: a failed delivery must not undo the registration
        try {
            send(user, channel, AppClock.currentUTC());
        } catch (RuntimeException e) {
            log.warn("Could not send {} verification code to new user {}: {}", channel, user.getId().getValue(),
                    e.getMessage());
        }
    }

    @Override
    @Transactional(noRollbackFor = VerificationException.class)
    public void confirm(VerificationChannel channel, String code) {
        AppUser user = currentUser();
        Instant now = AppClock.currentUTC();
        String userId = user.getId().getValue();
        if (isVerified(user, channel)) {
            throw new VerificationException(ALREADY_VERIFIED);
        }

        Optional<VerificationCode> latest = codeRepository.findLatestForUpdate(userId, channel);
        if (latest.isEmpty() || !latest.get().isUsable(now) || !latest.get().getDestination().equals(destination(user, channel))) {
            fail(user, channel, "NO_USABLE_CODE");
            throw new VerificationException(INVALID_CODE);
        }

        VerificationCode verificationCode = latest.get();
        if (!verificationCode.matches(code)) {
            boolean burned = verificationCode.registerFailedAttempt(policy.getMaxAttempts(), now);
            codeRepository.save(verificationCode);
            fail(user, channel, burned ? "BURNED" : "WRONG_CODE attempt=" + verificationCode.getFailedAttempts());
            throw new VerificationException(burned ? TOO_MANY_ATTEMPTS : WRONG_CODE);
        }

        verificationCode.consume(now);
        codeRepository.save(verificationCode);
        if (channel == VerificationChannel.PHONE) {
            user.verifyPhone(now);
        } else {
            user.verifyEmail(now);
        }
        userRepository.save(user);

        auditLog.record(AuthAuditEvent.of(channel == VerificationChannel.PHONE
                ? AuditEventType.PHONE_VERIFIED : AuditEventType.EMAIL_VERIFIED, userId, verificationCode.getDestination()));
        log.info("{} verified for user {}", channel, userId);
    }

    private VerificationSendResult send(AppUser user, VerificationChannel channel, Instant now) {
        String userId = user.getId().getValue();
        String destination = destination(user, channel);
        if (destination == null) {
            throw new VerificationException(NO_EMAIL);
        }
        if (isVerified(user, channel)) {
            throw new VerificationException(ALREADY_VERIFIED);
        }

        Optional<VerificationCode> latest = codeRepository.findLatestForUpdate(userId, channel);
        if (latest.isPresent()) {
            Instant resendAt = latest.get().getCreatedAt().plus(policy.getResendCooldown());
            if (now.isBefore(resendAt)) {
                throw new VerificationThrottledException(TOO_SOON, Duration.between(now, resendAt));
            }
        }
        if (codeRepository.countIssuedSince(userId, channel, now.minus(Duration.ofDays(1))) >= policy.getMaxCodesPerDay()) {
            throw new VerificationThrottledException(DAILY_LIMIT, Duration.ofHours(1));
        }

        codeRepository.invalidateActive(userId, channel, now);
        String code = OtpCode.create().getCode();
        VerificationCode verificationCode = VerificationCode.issue(snowflakeIdGenerator.generateId(), userId, channel,
                destination, code, now, policy.getCodeLifetime());
        codeRepository.save(verificationCode);

        String masked;
        if (channel == VerificationChannel.PHONE) {
            smsSender.send(destination, "Ma xac minh Sunvibe cua ban la " + code
                    + ". Ma het han sau " + policy.getCodeLifetime().toMinutes() + " phut. Khong chia se ma nay cho bat ky ai.");
            masked = ContactMasker.maskPhone(destination);
        } else {
            emailService.sendVerificationCodeEmail(destination, code);
            masked = ContactMasker.maskEmail(destination);
        }

        auditLog.record(AuthAuditEvent.of(channel == VerificationChannel.PHONE
                ? AuditEventType.PHONE_VERIFICATION_SENT : AuditEventType.EMAIL_VERIFICATION_SENT, userId, destination));
        return new VerificationSendResult(channel, masked, verificationCode.getExpiresAt(),
                now.plus(policy.getResendCooldown()));
    }

    private void fail(AppUser user, VerificationChannel channel, String reason) {
        auditLog.record(AuthAuditEvent.of(AuditEventType.VERIFICATION_FAILED, user.getId().getValue(),
                destination(user, channel), channel + " " + reason));
    }

    private static String destination(AppUser user, VerificationChannel channel) {
        if (channel == VerificationChannel.PHONE) {
            return user.getUsername().getValue();
        }
        return user.getEmail() != null ? user.getEmail().getValue() : null;
    }

    private static boolean isVerified(AppUser user, VerificationChannel channel) {
        return channel == VerificationChannel.PHONE ? user.isPhoneVerified() : user.isEmailVerified();
    }

    private AppUser currentUser() {
        return userRepository.findByUsername(CurrentUser.username());
    }
}
