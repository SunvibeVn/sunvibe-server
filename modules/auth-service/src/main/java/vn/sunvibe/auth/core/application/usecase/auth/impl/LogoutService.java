package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.auth.LogoutUseCase;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.common.time.AppClock;

@Log4j2
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final UserDomainRepository userRepository;

    @Override
    @Transactional
    public void execute(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenSecrets.hash(refreshToken))
                .filter(token -> !token.isRevoked())
                .map(RefreshToken::getFamilyId)
                .ifPresent(familyId -> {
                    refreshTokenRepository.revokeFamily(familyId, RevokeReason.LOGOUT, AppClock.currentUTC());
                    log.info("Session {} ended by logout", familyId);
                });
    }

    @Override
    @Transactional
    public void executeAllDevices() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("User not authenticated");
        }

        AppUser appUser = userRepository.findByUsername(authentication.getPrincipal().toString());
        refreshTokenRepository.revokeAllForUser(appUser.getId(), RevokeReason.LOGOUT_ALL, AppClock.currentUTC());
        log.info("All sessions of user {} ended by logout-all", appUser.getId().getValue());
    }
}
