package vn.sunvibe.auth.core.application.usecase.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.query.user.UserProfileResult;
import vn.sunvibe.auth.core.application.shared.CurrentUser;
import vn.sunvibe.auth.core.application.usecase.user.UserGetInfoUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

/**
 * Reads the signed-in user's own profile.
 */
@Service
@RequiredArgsConstructor
public class UserGetInfoService implements UserGetInfoUseCase {

    private final UserDomainRepository userRepository;

    @Override
    public UserProfileResult execute() {
        AppUser appUser = userRepository.findByUsername(CurrentUser.username());

        return UserProfileResult.builder()
                .fullName(appUser.getFullName())
                .username(appUser.getUsername().getValue())
                .email(appUser.getEmail() != null ? appUser.getEmail().getValue() : null)
                .profileImageUrl(appUser.getProfileImage() != null ? appUser.getProfileImage().getUrl() : null)
                .kycStatus(appUser.getKycStatus().name())
                .accountStatus(appUser.getUserStatus().name())
                .phoneVerified(appUser.isPhoneVerified())
                .emailVerified(appUser.isEmailVerified())
                .build();
    }

}
