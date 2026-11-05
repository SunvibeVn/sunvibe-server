package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.shared.CurrentUser;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.auth.LogoutUseCase;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.time.AppClock;

/**
 * Ends sign-in sessions by revoking their refresh tokens. Access tokens already
 * issued stay valid until they expire (minutes).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private static final String USER_NOT_FOUND = "User not found.";

    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final UserDomainRepository userRepository;
    private final AuditLogPort auditLog;

    @Override
    @Transactional
    public void execute(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String tokenHash = RefreshTokenSecrets.hash(refreshToken);
        // One lock order for every command: the user row first, or a reset and a logout deadlock on the family rows
        refreshTokenRepository.findUserIdByTokenHash(tokenHash).ifPresent(owner -> {
            userRepository.findByIdForSecurity(owner);
            refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                    .filter(token -> !token.isRevoked())
                    .ifPresent(token -> {
                        refreshTokenRepository.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT, AppClock.currentUTC());
                        log.info("Session {} ended by logout", token.getFamilyId());
                        auditLog.record(AuthAuditEvent.of(AuditEventType.LOGOUT, token.getUserId().getValue(), null,
                                "session=" + token.getFamilyId()));
                    });
        });
    }

    @Override
    @Transactional
    public void executeAllDevices() {
        // Under the lock, so a refresh in flight has committed its next token before this revokes it
        AppUser appUser = userRepository.findByIdForSecurity(UserId.from(CurrentUser.userId()))
                .map(LockedUser::user)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        refreshTokenRepository.revokeAllForUser(appUser.getId(), RevokeReason.LOGOUT_ALL, AppClock.currentUTC());
        log.info("All sessions of user {} ended by logout-all", appUser.getId().getValue());
        auditLog.record(AuthAuditEvent.of(AuditEventType.LOGOUT_ALL_DEVICES, appUser.getId().getValue(),
                appUser.usernameValue()));
    }
}
