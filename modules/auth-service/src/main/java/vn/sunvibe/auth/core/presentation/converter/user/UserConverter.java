package vn.sunvibe.auth.core.presentation.converter.user;

import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.application.query.user.UserProfileResult;
import vn.sunvibe.auth.core.presentation.dto.request.user.UserRegisterRequest;
import vn.sunvibe.auth.core.presentation.dto.response.user.UserProfileInfo;

/**
 * Converts user requests to commands and use-case results to response bodies.
 */
@Component
public class UserConverter {

    public UserRegisterCommand toUserRegisterCommand(UserRegisterRequest userRegisterRequest, ClientInfo clientInfo) {
        return UserRegisterCommand.builder()
                .fullName(userRegisterRequest.fullName())
                .username(userRegisterRequest.username())
                .email(userRegisterRequest.email())
                .password(userRegisterRequest.password())
                .idCardNumber(userRegisterRequest.idCardNumber())
                .registrationToken(userRegisterRequest.registrationToken())
                .clientInfo(clientInfo)
                .build();
    }

    public UserProfileInfo toUserProfileInfo(UserProfileResult profile) {
        return UserProfileInfo.builder()
                .fullName(profile.fullName())
                .username(profile.username())
                .email(profile.email())
                .profileImageUrl(profile.profileImageUrl())
                .kycStatus(profile.kycStatus())
                .accountStatus(profile.accountStatus())
                .phoneVerified(profile.phoneVerified())
                .emailVerified(profile.emailVerified())
                .build();
    }

}
