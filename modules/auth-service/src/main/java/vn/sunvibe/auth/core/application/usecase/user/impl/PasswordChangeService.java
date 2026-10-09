package vn.sunvibe.auth.core.application.usecase.user.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.usecase.user.PasswordChangeUseCase;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.common.time.AppClock;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordChangeResponse;

@Service
@RequiredArgsConstructor
public class PasswordChangeService implements PasswordChangeUseCase {

    private final UserDomainRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenDomainRepository refreshTokenRepository;

    @Override
    @Transactional
    public PasswordChangeResponse execute(PasswordChangeCommand passwordChangeCommand) {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("User not authenticated");
        }

        String username = authentication.getPrincipal().toString();
        AppUser appUser = userRepository.findByUsername(username);
        String newHashedPassword = passwordEncoder.encode(passwordChangeCommand.newPassword());

        if (!passwordEncoder.matches(passwordChangeCommand.currentPassword(), appUser.getPassword().getHashedValue())) {
            throw new AuthValidationException(AuthFieldName.CURRENT_PASSWORD, "Current password is incorrect.");
        }
        if (passwordEncoder.matches(passwordChangeCommand.currentPassword(), newHashedPassword)) {
            throw new AuthValidationException(AuthFieldName.NEW_PASSWORD, "New password must be different from current password.");
        }

        // Set new password for user
        appUser.changePassword(newHashedPassword);
        userRepository.save(appUser);

        // Sign out every device; the caller starts a fresh session for this one
        refreshTokenRepository.revokeAllForUser(appUser.getId(), RevokeReason.PASSWORD_CHANGED, AppClock.currentUTC());

        return PasswordChangeResponse.builder().username(username).build();
    }
}
