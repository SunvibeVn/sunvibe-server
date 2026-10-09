package vn.sunvibe.auth.core.application.usecase.user.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.usecase.user.UserGetInfoUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.presentation.dto.response.user.UserProfileInfo;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserGetInfoService implements UserGetInfoUseCase {

    private final UserDomainRepository userRepository;

    @Override
    public UserProfileInfo execute() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("User not authenticated");
        }

        String username = authentication.getPrincipal().toString();
        AppUser appUser = userRepository.findByUsername(username);

        return UserProfileInfo.builder()
                .fullName(appUser.getFullName())
                .username(appUser.getUsername().getValue())
                .email(appUser.getEmail().getValue())
                .profileImageUrl(appUser.getProfileImage().getUrl())
                .kycStatus(appUser.getKycStatus().name())
                .build();
    }

}
