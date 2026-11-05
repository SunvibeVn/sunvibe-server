package vn.sunvibe.auth.core.application.usecase.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.query.user.UserProfileResult;
import vn.sunvibe.auth.core.application.shared.AccountAccessPolicy;
import vn.sunvibe.auth.core.application.shared.CurrentUser;
import vn.sunvibe.auth.core.application.usecase.user.UserGetInfoUseCase;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

/**
 * Reads the signed-in user's own profile.
 */
@Service
@RequiredArgsConstructor
public class UserGetInfoService implements UserGetInfoUseCase {

    private static final String USER_NOT_FOUND = "User not found.";

    private final UserDomainRepository userRepository;
    private final AccountAccessPolicy accountAccess;

    @Override
    public UserProfileResult execute() {
        AppUser appUser = userRepository.findById(UserId.from(CurrentUser.userId()))
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

        return UserProfileResult.builder()
                .fullName(appUser.getFullName())
                .username(appUser.usernameValue())
                .email(appUser.getEmail() != null ? appUser.getEmail().getValue() : null)
                .profileImageUrl(appUser.getProfileImage() != null ? appUser.getProfileImage().getUrl() : null)
                .kycStatus(appUser.getKycStatus().name())
                .accountStatus(appUser.getUserStatus().name())
                .phoneVerified(appUser.isPhoneVerified())
                .emailVerified(appUser.isEmailVerified())
                .requiredAction(accountAccess.requiredAction(appUser))
                .build();
    }

}
