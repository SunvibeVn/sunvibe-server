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
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.common.time.AppClock;

/**
 * Ends sign-in sessions by revoking their refresh tokens. Access tokens already
 * issued stay valid until they expire (minutes).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final UserDomainRepository userRepository;
    private final AuditLogPort auditLog;

    @Override
    @Transactional
    public void execute(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenSecrets.hash(refreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    refreshTokenRepository.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT, AppClock.currentUTC());
                    log.info("Session {} ended by logout", token.getFamilyId());
                    auditLog.record(AuthAuditEvent.of(AuditEventType.LOGOUT, token.getUserId().getValue(), null,
                            "session=" + token.getFamilyId()));
                });
    }

    @Override
    @Transactional
    public void executeAllDevices() {
        AppUser appUser = userRepository.findByUsername(CurrentUser.username());
        refreshTokenRepository.revokeAllForUser(appUser.getId(), RevokeReason.LOGOUT_ALL, AppClock.currentUTC());
        log.info("All sessions of user {} ended by logout-all", appUser.getId().getValue());
        auditLog.record(AuthAuditEvent.of(AuditEventType.LOGOUT_ALL_DEVICES, appUser.getId().getValue(),
                appUser.getUsername().getValue()));
    }
}
