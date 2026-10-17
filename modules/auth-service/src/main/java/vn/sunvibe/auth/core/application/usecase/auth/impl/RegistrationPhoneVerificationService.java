package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationPhoneVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationPhoneChallenge;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationPhoneChallengeRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Phone verification before registration. One usable code per phone number: a
 * new code burns the previous one. Codes are throttled (cooldown + daily cap),
 * tolerate a few wrong guesses and, once confirmed, are exchanged for a
 * single-use registration token. Failed guesses are committed even though the
 * request fails ({@code noRollbackFor}).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class RegistrationPhoneVerificationService implements RegistrationPhoneVerificationUseCase {

    static final String PHONE_IN_USE = "Phone number is already in use.";
    static final String INVALID_CODE = "The verification code is invalid or has expired. Please request a new one.";
    static final String WRONG_CODE = "Incorrect verification code.";
    static final String TOO_MANY_ATTEMPTS = "Too many incorrect codes. Please request a new one.";
    static final String TOO_SOON = "Please wait before requesting another code.";
    static final String DAILY_LIMIT = "Too many codes requested today. Please try again later.";
    static final String INVALID_TOKEN = "Your phone verification is invalid or has expired. Please verify your phone number again.";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final UserDomainRepository userRepository;
    private final RegistrationPhoneChallengeRepository challengeRepository;
    private final SmsSenderPort smsSender;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final VerificationPolicy policy;
    private final AuditLogPort auditLog;

    @Override
    @Transactional
    public VerificationSendResult sendCode(String phoneNumber) {
        String phone = phoneNumber.trim();
        Instant now = AppClock.currentUTC();

        // Same rule and message as registration itself: a taken number never gets a code
        if (userRepository.existsByUsername(phone)) {
            throw new AuthValidationException(AuthFieldName.USER_NAME, PHONE_IN_USE);
        }

        Optional<RegistrationPhoneChallenge> latest = challengeRepository.findLatestForUpdate(phone);
        if (latest.isPresent()) {
            Instant resendAt = latest.get().getCreatedAt().plus(policy.getResendCooldown());
            if (now.isBefore(resendAt)) {
                throw new VerificationThrottledException(VerificationThrottledException.Reason.RESEND_TOO_SOON, TOO_SOON,
                        Duration.between(now, resendAt));
            }
        }
        if (challengeRepository.countIssuedSince(phone, now.minus(Duration.ofDays(1))) >= policy.getMaxCodesPerDay()) {
            throw new VerificationThrottledException(VerificationThrottledException.Reason.DAILY_LIMIT, DAILY_LIMIT,
                    Duration.ofHours(1));
        }

        challengeRepository.invalidateActive(phone, now);
        String code = OtpCode.create().getCode();
        RegistrationPhoneChallenge challenge = RegistrationPhoneChallenge.issue(snowflakeIdGenerator.generateId(),
                phone, code, now, policy.getCodeLifetime());
        challengeRepository.save(challenge);

        smsSender.send(phone, "Ma xac minh dang ky Sunvibe cua ban la " + code + ". Ma het han sau "
                + policy.getCodeLifetime().toMinutes() + " phut. Khong chia se ma nay cho bat ky ai.");

        auditLog.record(AuthAuditEvent.of(AuditEventType.REGISTRATION_PHONE_CODE_SENT, null, phone));
        return new VerificationSendResult(VerificationChannel.PHONE, ContactMasker.maskPhone(phone),
                challenge.getExpiresAt(), now.plus(policy.getResendCooldown()));
    }

    @Override
    @Transactional(noRollbackFor = VerificationException.class)
    public RegistrationTokenResult verifyCode(String phoneNumber, String code) {
        String phone = phoneNumber.trim();
        Instant now = AppClock.currentUTC();

        Optional<RegistrationPhoneChallenge> latest = challengeRepository.findLatestForUpdate(phone);
        if (latest.isEmpty() || !latest.get().isUsable(now)) {
            fail(phone, "NO_USABLE_CODE");
            throw new VerificationException(VerificationException.Reason.CODE_EXPIRED, INVALID_CODE);
        }

        RegistrationPhoneChallenge challenge = latest.get();
        if (!challenge.matches(code)) {
            boolean burned = challenge.registerFailedAttempt(policy.getMaxAttempts(), now);
            challengeRepository.save(challenge);
            fail(phone, burned ? "BURNED" : "WRONG_CODE attempt=" + challenge.getFailedAttempts());
            throw burned
                    ? new VerificationException(VerificationException.Reason.CODE_ATTEMPTS_EXCEEDED, TOO_MANY_ATTEMPTS)
                    : new VerificationException(VerificationException.Reason.CODE_INCORRECT, WRONG_CODE);
        }

        String token = newToken();
        challenge.verify(token, now, policy.getRegistrationTokenLifetime());
        challengeRepository.save(challenge);

        auditLog.record(AuthAuditEvent.of(AuditEventType.REGISTRATION_PHONE_VERIFIED, null, phone));
        log.info("Phone {} verified for registration", ContactMasker.maskPhone(phone));
        return new RegistrationTokenResult(token, challenge.getTokenExpiresAt());
    }

    @Override
    @Transactional(noRollbackFor = VerificationException.class)
    public void consumeRegistrationToken(String registrationToken, String phoneNumber) {
        String phone = phoneNumber.trim();
        Instant now = AppClock.currentUTC();

        Optional<RegistrationPhoneChallenge> found = registrationToken == null || registrationToken.isBlank()
                ? Optional.empty()
                : challengeRepository.findByTokenHashForUpdate(RegistrationPhoneChallenge.hashToken(registrationToken));
        if (found.isEmpty() || !found.get().isTokenUsable(now) || !found.get().getPhone().equals(phone)) {
            fail(phone, "TOKEN_REJECTED");
            throw new VerificationException(VerificationException.Reason.REGISTRATION_TOKEN_INVALID, INVALID_TOKEN);
        }

        RegistrationPhoneChallenge challenge = found.get();
        challenge.markRegistered(now);
        challengeRepository.save(challenge);
    }

    private void fail(String phone, String reason) {
        auditLog.record(AuthAuditEvent.of(AuditEventType.REGISTRATION_VERIFICATION_FAILED, null, phone, reason));
    }

    /** 32 random bytes, URL-safe, so it can travel in a JSON body or a cookie unchanged. */
    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
