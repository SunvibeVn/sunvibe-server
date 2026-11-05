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
import vn.sunvibe.auth.core.application.shared.OtpHasher;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.ContactVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.VerificationCodeRepository;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Phone (SMS) verification codes for the signed-in user.
 * <p>
 * One usable code per account: a new code burns the previous one. Codes are throttled (cooldown + daily cap), tolerate a
 * few wrong guesses and are bound to the number they were sent to. Failed guesses are committed even though the request
 * fails ({@code noRollbackFor}).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ContactVerificationService implements ContactVerificationUseCase {

    private static final String USER_NOT_FOUND = "User not found.";
    static final String ALREADY_VERIFIED = "This contact is already verified.";
    static final String NO_PHONE = "There is no phone on this account to verify.";
    static final String INVALID_CODE = "The verification code is invalid or has expired. Please request a new one.";
    static final String WRONG_CODE = "Incorrect verification code.";
    static final String TOO_MANY_ATTEMPTS = "Too many incorrect codes. Please request a new one.";
    static final String TOO_SOON = "Please wait before requesting another code.";
    static final String DAILY_LIMIT = "Too many codes requested today. Please try again later.";

    private final UserDomainRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final SmsSenderPort smsSender;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final VerificationPolicy policy;
    private final AuditLogPort auditLog;
    private final PhoneChannelPolicy phoneChannel;
    private final OtpHasher otpHasher;

    @Override
    @Transactional
    public VerificationSendResult sendCode() {
        phoneChannel.require(PhoneChannelPolicy.Operation.VERIFY);
        AppUser user = userRepository.findById(UserId.from(CurrentUser.userId()))
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        return send(user, AppClock.currentUTC());
    }

    @Override
    @Transactional
    public void sendCodeAfterRegistration(AppUser user) {
        // Exceptions must not leave this method: a failed delivery must not undo the registration
        try {
            send(user, AppClock.currentUTC());
        } catch (RuntimeException e) {
            log.warn("Could not send a verification code to new user {}: {}", user.getId().getValue(),
                    e.getClass().getSimpleName());
        }
    }

    @Override
    @Transactional(noRollbackFor = VerificationException.class)
    public void confirm(String code) {
        phoneChannel.require(PhoneChannelPolicy.Operation.VERIFY);
        LockedUser locked = userRepository.findByIdForSecurity(UserId.from(CurrentUser.userId()))
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        AppUser user = locked.user();
        Instant now = AppClock.currentUTC();
        String userId = user.getId().getValue();
        if (user.isPhoneVerified()) {
            throw new VerificationException(VerificationException.Reason.ALREADY_VERIFIED, ALREADY_VERIFIED);
        }

        Optional<VerificationCode> latest = codeRepository.findLatestForUpdate(userId, VerificationChannel.PHONE);
        if (latest.isEmpty() || !latest.get().isUsable(now) || !latest.get().getDestination().equals(destination(user))) {
            fail(user, "NO_USABLE_CODE");
            throw new VerificationException(VerificationException.Reason.CODE_EXPIRED, INVALID_CODE);
        }

        VerificationCode verificationCode = latest.get();
        if (!verificationCode.matches(otpHasher.hash(verificationCode.getId(), code))) {
            boolean burned = verificationCode.registerFailedAttempt(policy.getMaxAttempts(), now);
            codeRepository.save(verificationCode);
            fail(user, burned ? "BURNED" : "WRONG_CODE attempt=" + verificationCode.getFailedAttempts());
            throw burned
                    ? new VerificationException(VerificationException.Reason.CODE_ATTEMPTS_EXCEEDED, TOO_MANY_ATTEMPTS)
                    : new VerificationException(VerificationException.Reason.CODE_INCORRECT, WRONG_CODE);
        }

        verificationCode.consume(now);
        codeRepository.save(verificationCode);
        user.verifyPhone(now);
        userRepository.save(locked);

        auditLog.record(AuthAuditEvent.of(AuditEventType.PHONE_VERIFIED, userId, verificationCode.getDestination()));
        log.info("Phone verified for user {}", userId);
    }

    private VerificationSendResult send(AppUser user, Instant now) {
        String userId = user.getId().getValue();
        String destination = destination(user);
        if (destination == null) {
            throw new VerificationException(VerificationException.Reason.NO_PHONE, NO_PHONE);
        }
        if (user.isPhoneVerified()) {
            throw new VerificationException(VerificationException.Reason.ALREADY_VERIFIED, ALREADY_VERIFIED);
        }

        Optional<VerificationCode> latest = codeRepository.findLatestForUpdate(userId, VerificationChannel.PHONE);
        if (latest.isPresent()) {
            Instant resendAt = latest.get().getCreatedAt().plus(policy.getResendCooldown());
            if (now.isBefore(resendAt)) {
                throw new VerificationThrottledException(VerificationThrottledException.Reason.RESEND_TOO_SOON, TOO_SOON,
                        Duration.between(now, resendAt));
            }
        }
        if (codeRepository.countIssuedSince(userId, VerificationChannel.PHONE, now.minus(Duration.ofDays(1)))
                >= policy.getMaxCodesPerDay()) {
            throw new VerificationThrottledException(VerificationThrottledException.Reason.DAILY_LIMIT, DAILY_LIMIT,
                    Duration.ofHours(1));
        }

        codeRepository.invalidateActive(userId, VerificationChannel.PHONE, now);
        String code = OtpCode.create().getCode();
        Long id = snowflakeIdGenerator.generateId();
        VerificationCode verificationCode = VerificationCode.issue(id, userId, VerificationChannel.PHONE, destination,
                otpHasher.hash(id, code), now, policy.getCodeLifetime());
        codeRepository.save(verificationCode);

        smsSender.send(destination, "Ma xac minh Sunvibe cua ban la " + code
                + ". Ma het han sau " + policy.getCodeLifetime().toMinutes() + " phut. Khong chia se ma nay cho bat ky ai.");

        auditLog.record(AuthAuditEvent.of(AuditEventType.PHONE_VERIFICATION_SENT, userId, destination));
        return new VerificationSendResult(VerificationChannel.PHONE, ContactMasker.maskPhone(user.usernameValue()),
                verificationCode.getExpiresAt(), now.plus(policy.getResendCooldown()));
    }

    private void fail(AppUser user, String reason) {
        auditLog.record(AuthAuditEvent.of(AuditEventType.VERIFICATION_FAILED, user.getId().getValue(), destination(user),
                VerificationChannel.PHONE + " " + reason));
    }

    /** The key where the row has one: a row without a key still has its stored number. */
    private static String destination(AppUser user) {
        return user.getPhoneKey() != null ? user.getPhoneKey() : user.usernameValue();
    }
}
