package vn.sunvibe.auth.core.application.usecase.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.user.EmailBindConfirmCommand;
import vn.sunvibe.auth.core.application.command.user.EmailBindSendCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountDisabledException;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.shared.CurrentUser;
import vn.sunvibe.auth.core.application.shared.OtpHasher;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.usecase.external.email.MailSendGatePort;
import vn.sunvibe.auth.core.application.usecase.external.lock.AuthAdvisoryLockPort;
import vn.sunvibe.auth.core.application.usecase.user.EmailBindingUseCase;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.application.usecase.user.PasswordReauthenticationUseCase;
import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;
import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.EmailNotificationRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.auth.VerificationCodeRepository;
import vn.sunvibe.auth.core.domain.repository.user.EmailOccupancy;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Bind and replace of the account email (auth-email-access spec 4.9, 5.4, 5.5).
 * <p>
 * {@link #sendCode} holds no row lock through bcrypt or SMTP: the password is checked in its own transaction, the mail
 * gate is entered before the send transaction, and that transaction takes advisory locks only. The previous proof is read
 * without a lock and burned after the mail, so a confirm, which holds the users row, never waits on SMTP.
 * <p>
 * {@link #confirm} locks the row first. An address on another account is found only after the proof: the code is used up
 * and that commits ({@code noRollbackFor}), while a race lost at the unique index rolls back, so that code stays until it
 * expires, harmlessly.
 */
@Service
@RequiredArgsConstructor
public class EmailBindingService implements EmailBindingUseCase {

    static final String WRONG_PASSWORD = "Current password is incorrect.";
    static final String ALREADY_VERIFIED = "This email is already verified on the account.";
    static final String INVALID_CODE = "The verification code is invalid or has expired. Please request a new one.";
    static final String WRONG_CODE = "Incorrect verification code.";
    static final String TOO_MANY_ATTEMPTS = "Too many incorrect codes. Please request a new one.";
    static final String TOO_SOON = "Please wait before requesting another code.";
    static final String DAILY_LIMIT = "Too many codes requested today. Please try again later.";
    static final String NOT_AVAILABLE = "This email cannot be used now.";
    static final String MAIL_REFUSED = "The mail could not be sent now.";
    private static final String USER_NOT_FOUND = "User not found.";

    private final UserDomainRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final EmailNotificationRepository notificationRepository;
    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final PasswordReauthenticationUseCase reauthentication;
    private final LoginUseCase loginUseCase;
    private final EmailServicePort emailService;
    private final MailSendGatePort mailGate;
    private final AuthAdvisoryLockPort advisoryLock;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final VerificationPolicy policy;
    private final AuditLogPort auditLog;
    private final TransactionTemplate transaction;
    private final OtpHasher otpHasher;

    @Override
    public VerificationSendResult sendCode(EmailBindSendCommand command) {
        String userId = CurrentUser.userId();
        String emailKey = Email.create(command.newEmail()).getValue();

        ReauthenticationResult check = reauthentication.check(userId, command.currentPassword());
        switch (check.outcome()) {
            case LOCKED -> {
                fail(userId, "REAUTHENTICATION_LOCKED");
                throw new AccountTemporarilyLockedException(check.lockedFor());
            }
            case WRONG -> {
                fail(userId, "WRONG_PASSWORD");
                throw new AuthValidationException(AuthFieldName.CURRENT_PASSWORD, WRONG_PASSWORD);
            }
            case OK -> { }
        }

        if (!mailGate.tryEnter()) {
            throw new SendingMailFailedException(MAIL_REFUSED);
        }
        try {
            return transaction.execute(status -> send(userId, emailKey, check.credentialVersion()));
        } finally {
            mailGate.leave();
        }
    }

    private VerificationSendResult send(String userId, String emailKey, long credentialVersion) {
        Instant now = AppClock.currentUTC();
        // Always the user before the destination, so two sends cannot wait on each other
        if (!advisoryLock.tryLock("bind-user:" + userId) || !advisoryLock.tryLock("bind-dest:" + emailKey)) {
            throw tooSoon(policy.getResendCooldown());
        }

        AppUser user = userRepository.findById(UserId.from(userId)).orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        if (user.isEmailVerified() && emailKey.equals(user.getEmailKey())) {
            throw new VerificationException(VerificationException.Reason.ALREADY_VERIFIED, ALREADY_VERIFIED);
        }
        throttle(userId, emailKey, now);

        String code = OtpCode.create().getCode();
        Long id = snowflakeIdGenerator.generateId();
        VerificationCode verificationCode = VerificationCode.issueForBind(id, userId, emailKey, otpHasher.hash(id, code),
                credentialVersion, now, policy.getCodeLifetime());
        codeRepository.insert(verificationCode);

        if (!mailGate.tryCharge()) {
            throw new SendingMailFailedException(MAIL_REFUSED);
        }
        emailService.sendEmailBindCodeEmail(emailKey, code);
        codeRepository.burnOtherBindCodes(userId, verificationCode.getId(), now);

        String masked = ContactMasker.maskEmail(emailKey);
        auditLog.record(AuthAuditEvent.of(AuditEventType.EMAIL_BIND_CODE_SENT, userId, masked));
        return new VerificationSendResult(VerificationChannel.EMAIL, masked, verificationCode.getExpiresAt(),
                now.plus(policy.getResendCooldown()));
    }

    private void throttle(String userId, String emailKey, Instant now) {
        Optional<VerificationCode> latest = codeRepository.findLatestBind(userId);
        if (latest.isPresent()) {
            Instant resendAt = latest.get().getCreatedAt().plus(policy.getResendCooldown());
            if (now.isBefore(resendAt)) {
                throw tooSoon(Duration.between(now, resendAt));
            }
        }
        Instant dayAgo = now.minus(Duration.ofDays(1));
        if (codeRepository.countBindIssuedSince(userId, dayAgo) >= policy.getMaxCodesPerDay()
                || codeRepository.countBindIssuedToDestinationSince(emailKey, dayAgo) >= policy.getMaxCodesPerDay()) {
            throw new VerificationThrottledException(VerificationThrottledException.Reason.DAILY_LIMIT, DAILY_LIMIT,
                    Duration.ofHours(1));
        }
    }

    @Override
    @Transactional(noRollbackFor = VerificationException.class)
    public AuthResult confirm(EmailBindConfirmCommand command) {
        String userId = CurrentUser.userId();
        Email newEmail = Email.create(command.newEmail());
        String emailKey = newEmail.getValue();
        Instant now = AppClock.currentUTC();

        LockedUser locked = userRepository.findByIdForSecurity(UserId.from(userId))
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        AppUser user = locked.user();
        if (!user.canAuthenticate()) {
            fail(userId, "ACCOUNT_DISABLED");
            throw new AccountDisabledException();
        }

        VerificationCode proof = usableProof(userId, emailKey, user.getCredentialVersion(), now);
        if (!proof.matches(otpHasher.hash(proof.getId(), command.code()))) {
            boolean burned = proof.registerFailedAttempt(policy.getMaxAttempts(), now);
            codeRepository.save(proof);
            fail(userId, burned ? "BURNED" : "WRONG_CODE attempt=" + proof.getFailedAttempts());
            throw burned
                    ? new VerificationException(VerificationException.Reason.CODE_ATTEMPTS_EXCEEDED, TOO_MANY_ATTEMPTS)
                    : new VerificationException(VerificationException.Reason.CODE_INCORRECT, WRONG_CODE);
        }

        proof.consume(now);
        codeRepository.save(proof);
        if (userRepository.emailOccupancy(emailKey, user.getId()) != EmailOccupancy.FREE) {
            fail(userId, "ADDRESS_TAKEN");
            throw new VerificationException(VerificationException.Reason.EMAIL_NOT_AVAILABLE, NOT_AVAILABLE);
        }

        Optional<String> replaced = user.isEmailVerified() ? Optional.of(user.getEmail().getValue()) : Optional.empty();
        user.bindVerifiedEmail(newEmail, now);
        userRepository.save(locked);

        // Whoever held the old address, or a session on it, is out: the password change does the same
        refreshTokenRepository.revokeAllForUser(user.getId(), RevokeReason.EMAIL_CHANGED, now);
        ClientInfo clientInfo = command.clientInfo() != null ? command.clientInfo() : ClientInfo.unknown();
        AuthResult result = loginUseCase.loginInternal(user, clientInfo);

        replaced.ifPresent(oldAddress -> notificationRepository.save(EmailNotification.emailChanged(
                snowflakeIdGenerator.generateId(), userId, oldAddress, now)));
        auditLog.record(AuthAuditEvent.of(replaced.isPresent() ? AuditEventType.EMAIL_REPLACED : AuditEventType.EMAIL_BOUND,
                userId, ContactMasker.maskEmail(emailKey)));
        return result;
    }

    /** The newest proof of this purpose, for this address and this credential version; anything else is no proof. */
    private VerificationCode usableProof(String userId, String emailKey, long credentialVersion, Instant now) {
        return codeRepository.findLatestBindForUpdate(userId)
                .filter(code -> code.isUsable(now))
                .filter(code -> emailKey.equals(code.getDestination()))
                .filter(code -> Long.valueOf(credentialVersion).equals(code.getCredentialVersion()))
                .orElseThrow(() -> {
                    fail(userId, "NO_USABLE_CODE");
                    return new VerificationException(VerificationException.Reason.CODE_EXPIRED, INVALID_CODE);
                });
    }

    private static VerificationThrottledException tooSoon(Duration retryAfter) {
        return new VerificationThrottledException(VerificationThrottledException.Reason.RESEND_TOO_SOON, TOO_SOON, retryAfter);
    }

    private void fail(String userId, String reason) {
        auditLog.record(AuthAuditEvent.of(AuditEventType.EMAIL_BIND_FAILED, userId, null, reason));
    }
}
