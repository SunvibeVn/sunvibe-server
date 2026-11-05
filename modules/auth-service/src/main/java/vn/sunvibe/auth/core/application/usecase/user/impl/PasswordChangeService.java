package vn.sunvibe.auth.core.application.usecase.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.CurrentUser;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.application.usecase.user.PasswordChangeUseCase;
import vn.sunvibe.auth.core.application.usecase.user.PasswordReauthenticationUseCase;
import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.shared.time.AppClock;

/**
 * Password change by the signed-in user.
 * <p>
 * The current password is checked first, in the reauthentication use case, which counts a wrong one so that a stolen
 * access token cannot guess without limit (auth-email-access D4-12, S-44). That use case has its own transaction, so this
 * one is not {@code @Transactional}: the write runs afterwards in a transaction of its own, compares the credential
 * version under the row lock and encodes under it.
 * <p>
 * Every existing session is ended (whoever learned the old password may hold one) and a fresh session is started for the
 * requesting device in the same transaction. That new session does not go through the password login again, so a
 * temporary login lockout cannot leave the user signed out everywhere.
 */
@Service
@RequiredArgsConstructor
public class PasswordChangeService implements PasswordChangeUseCase {

    private static final String USER_NOT_FOUND = "User not found.";
    private static final String WRONG_PASSWORD = "Current password is incorrect.";

    private final UserDomainRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final AuditLogPort auditLog;
    private final LoginUseCase loginUseCase;
    private final PasswordReauthenticationUseCase reauthentication;
    private final TransactionTemplate transaction;

    @Override
    public AuthResult execute(PasswordChangeCommand command) {
        String userId = CurrentUser.userId();
        ReauthenticationResult check = reauthentication.check(userId, command.currentPassword());
        switch (check.outcome()) {
            case LOCKED -> {
                audit(AuditEventType.PASSWORD_CHANGE_FAILED, userId, "REAUTHENTICATION_LOCKED");
                throw new AccountTemporarilyLockedException(check.lockedFor());
            }
            case WRONG -> {
                audit(AuditEventType.PASSWORD_CHANGE_FAILED, userId, "WRONG_CURRENT_PASSWORD");
                throw new AuthValidationException(AuthFieldName.CURRENT_PASSWORD, WRONG_PASSWORD);
            }
            case OK -> { }
        }
        if (command.currentPassword().equals(command.newPassword())) {
            throw new AuthValidationException(AuthFieldName.NEW_PASSWORD, "New password must be different from current password.");
        }

        return transaction.execute(status -> change(UserId.from(userId), check.credentialVersion(), command));
    }

    private AuthResult change(UserId userId, long checkedVersion, PasswordChangeCommand command) {
        LockedUser locked = userRepository.findByIdForSecurity(userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        AppUser appUser = locked.user();
        // The password was checked without a lock: a change that committed meanwhile wins
        if (appUser.getCredentialVersion() != checkedVersion) {
            audit(AuditEventType.PASSWORD_CHANGE_FAILED, userId.getValue(), "CREDENTIALS_CHANGED");
            throw new AuthValidationException(AuthFieldName.CURRENT_PASSWORD, WRONG_PASSWORD);
        }

        appUser.changePassword(passwordEncoder.encode(command.newPassword()));
        userRepository.save(locked);

        // Sign out every device, then start a fresh session for this one
        refreshTokenRepository.revokeAllForUser(userId, RevokeReason.PASSWORD_CHANGED, AppClock.currentUTC());
        audit(AuditEventType.PASSWORD_CHANGED, userId.getValue(), null);

        ClientInfo clientInfo = command.clientInfo() != null ? command.clientInfo() : ClientInfo.unknown();
        return loginUseCase.loginInternal(appUser, clientInfo);
    }

    private void audit(AuditEventType type, String userId, String details) {
        auditLog.record(AuthAuditEvent.of(type, userId, null, details));
    }
}
