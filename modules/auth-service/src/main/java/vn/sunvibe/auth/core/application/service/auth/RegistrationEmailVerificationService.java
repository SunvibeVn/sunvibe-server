package vn.sunvibe.auth.core.application.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.shared.OtpHasher;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationEmailVerificationUseCase;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.shared.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.shared.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.shared.external.email.MailSendGatePort;
import vn.sunvibe.auth.core.application.shared.external.lock.AuthAdvisoryLockPort;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationEmailChallenge;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationEmailChallengeRepository;
import vn.sunvibe.auth.core.domain.repository.user.EmailOccupancy;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Proof of a mailbox before registration (auth-email-access spec 4.7, 4.8, 5.4).
 * <p>
 * {@link #sendCode} is not {@code @Transactional}: it enters {@link MailSendGatePort} before the transaction, so a
 * refused request holds no connection, and the row is flushed before the mail inside it, so a failed mail rolls the row
 * back and a retry is free. Failed guesses of {@link #verifyCode} are committed although the request fails
 * ({@code noRollbackFor}).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class RegistrationEmailVerificationService implements RegistrationEmailVerificationUseCase {

    static final String INVALID_CODE = "The verification code is invalid or has expired. Please request a new one.";
    static final String WRONG_CODE = "Incorrect verification code.";
    static final String TOO_MANY_ATTEMPTS = "Too many incorrect codes. Please request a new one.";
    static final String TOO_SOON = "Please wait before requesting another code.";
    static final String DAILY_LIMIT = "Too many codes requested today. Please try again later.";
    static final String ACCOUNT_EXISTS = "An account already uses this email.";
    static final String NOT_AVAILABLE = "This email cannot be used now.";
    static final String MAIL_REFUSED = "The mail could not be sent now.";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final UserDomainRepository userRepository;
    private final RegistrationEmailChallengeRepository challengeRepository;
    private final EmailServicePort emailService;
    private final MailSendGatePort mailGate;
    private final AuthAdvisoryLockPort advisoryLock;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final OtpHasher otpHasher;
    private final VerificationPolicy policy;
    private final AuditLogPort auditLog;
    private final TransactionTemplate transaction;

    @Override
    public VerificationSendResult sendCode(String email) {
        String emailKey = Email.create(email).getValue();
        if (!mailGate.tryEnter()) {
            throw new SendingMailFailedException(MAIL_REFUSED);
        }
        try {
            return transaction.execute(status -> send(emailKey));
        } finally {
            mailGate.leave();
        }
    }

    private VerificationSendResult send(String emailKey) {
        Instant now = AppClock.currentUTC();
        if (!advisoryLock.tryLock("reg-email:" + emailKey)) {
            throw tooSoon(policy.getResendCooldown());
        }

        Optional<RegistrationEmailChallenge> latest = challengeRepository.findLatest(emailKey);
        if (latest.isPresent()) {
            Instant resendAt = latest.get().getCreatedAt().plus(policy.getResendCooldown());
            if (now.isBefore(resendAt)) {
                throw tooSoon(Duration.between(now, resendAt));
            }
        }
        if (challengeRepository.countIssuedSince(emailKey, now.minus(Duration.ofDays(1))) >= policy.getMaxCodesPerDay()) {
            throw new VerificationThrottledException(VerificationThrottledException.Reason.DAILY_LIMIT, DAILY_LIMIT,
                    Duration.ofHours(1));
        }

        challengeRepository.burnActiveCodes(emailKey, now);
        String code = OtpCode.create().getCode();
        Long id = snowflakeIdGenerator.generateId();
        RegistrationEmailChallenge challenge = RegistrationEmailChallenge.issue(id, emailKey, otpHasher.hash(id, code), now,
                policy.getCodeLifetime());
        if (!challengeRepository.insert(challenge)) {
            throw tooSoon(policy.getResendCooldown());
        }

        if (!mailGate.tryCharge()) {
            throw new SendingMailFailedException(MAIL_REFUSED);
        }
        emailService.sendRegistrationCodeEmail(emailKey, code);

        String masked = ContactMasker.maskEmail(emailKey);
        auditLog.record(AuthAuditEvent.of(AuditEventType.REGISTRATION_EMAIL_CODE_SENT, null, masked));
        return new VerificationSendResult(VerificationChannel.EMAIL, masked, challenge.getExpiresAt(),
                now.plus(policy.getResendCooldown()));
    }

    @Override
    @Transactional(noRollbackFor = VerificationException.class)
    public RegistrationTokenResult verifyCode(String email, String code) {
        String emailKey = Email.create(email).getValue();
        Instant now = AppClock.currentUTC();

        Optional<RegistrationEmailChallenge> latest = challengeRepository.findLatestForUpdate(emailKey);
        if (latest.isEmpty() || !latest.get().isUsable(now)) {
            fail(emailKey, "NO_USABLE_CODE");
            throw new VerificationException(VerificationException.Reason.CODE_EXPIRED, INVALID_CODE);
        }

        RegistrationEmailChallenge challenge = latest.get();
        if (!challenge.matches(otpHasher.hash(challenge.getId(), code))) {
            boolean burned = challenge.registerFailedAttempt(policy.getMaxAttempts(), now);
            challengeRepository.save(challenge);
            fail(emailKey, burned ? "BURNED" : "WRONG_CODE attempt=" + challenge.getFailedAttempts());
            throw burned
                    ? new VerificationException(VerificationException.Reason.CODE_ATTEMPTS_EXCEEDED, TOO_MANY_ATTEMPTS)
                    : new VerificationException(VerificationException.Reason.CODE_INCORRECT, WRONG_CODE);
        }

        // Only the holder of the mailbox gets here, so only they learn who holds the address
        EmailOccupancy occupancy = userRepository.emailOccupancy(emailKey, null);
        if (occupancy != EmailOccupancy.FREE) {
            challenge.consume(now);
            challengeRepository.save(challenge);
            fail(emailKey, occupancy.name());
            throw occupancy == EmailOccupancy.VERIFIED
                    ? new VerificationException(VerificationException.Reason.EMAIL_ACCOUNT_EXISTS, ACCOUNT_EXISTS)
                    : new VerificationException(VerificationException.Reason.EMAIL_NOT_AVAILABLE, NOT_AVAILABLE);
        }

        String token = newToken();
        challenge.verify(token, now, policy.getRegistrationTokenLifetime());
        challengeRepository.save(challenge);

        auditLog.record(AuthAuditEvent.of(AuditEventType.REGISTRATION_EMAIL_VERIFIED, null, ContactMasker.maskEmail(emailKey)));
        return new RegistrationTokenResult(token, challenge.getTokenExpiresAt());
    }

    private static VerificationThrottledException tooSoon(Duration retryAfter) {
        return new VerificationThrottledException(VerificationThrottledException.Reason.RESEND_TOO_SOON, TOO_SOON, retryAfter);
    }

    private void fail(String emailKey, String reason) {
        auditLog.record(AuthAuditEvent.of(AuditEventType.REGISTRATION_VERIFICATION_FAILED, null,
                ContactMasker.maskEmail(emailKey), reason));
    }

    /** 32 random bytes, URL-safe, so it can travel in a JSON body unchanged. */
    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
