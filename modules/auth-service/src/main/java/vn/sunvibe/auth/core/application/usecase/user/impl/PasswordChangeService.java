package vn.sunvibe.auth.core.application.usecase.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.CurrentUser;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.application.usecase.user.PasswordChangeUseCase;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.shared.time.AppClock;

/**
 * Password change by the signed-in user.
 * <p>
 * Every existing session is ended (whoever learned the old password may hold
 * one) and a fresh session is started for the requesting device in the same
 * transaction. That new session does not go through the password login again,
 * so a temporary login lockout cannot leave the user signed out everywhere.
 */
@Service
@RequiredArgsConstructor
public class PasswordChangeService implements PasswordChangeUseCase {

    private final UserDomainRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final AuditLogPort auditLog;
    private final LoginUseCase loginUseCase;

    @Override
    @Transactional
    public AuthResult execute(PasswordChangeCommand command) {
        String username = CurrentUser.username();
        AppUser appUser = userRepository.findByUsername(username);

        if (!passwordEncoder.matches(command.currentPassword(), appUser.getPassword().getHashedValue())) {
            auditLog.record(AuthAuditEvent.of(AuditEventType.PASSWORD_CHANGE_FAILED, appUser.getId().getValue(),
                    username, "WRONG_CURRENT_PASSWORD"));
            throw new AuthValidationException(AuthFieldName.CURRENT_PASSWORD, "Current password is incorrect.");
        }
        if (command.currentPassword().equals(command.newPassword())) {
            throw new AuthValidationException(AuthFieldName.NEW_PASSWORD, "New password must be different from current password.");
        }

        appUser.changePassword(passwordEncoder.encode(command.newPassword()));
        userRepository.save(appUser);

        // Sign out every device, then start a fresh session for this one
        refreshTokenRepository.revokeAllForUser(appUser.getId(), RevokeReason.PASSWORD_CHANGED, AppClock.currentUTC());
        auditLog.record(AuthAuditEvent.of(AuditEventType.PASSWORD_CHANGED, appUser.getId().getValue(), username));

        ClientInfo clientInfo = command.clientInfo() != null ? command.clientInfo() : ClientInfo.unknown();
        return loginUseCase.loginInternal(appUser, clientInfo);
    }
}
