package vn.sunvibe.auth.core.presentation.converter.user;

import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.presentation.dto.request.user.UserRegisterRequest;

@Component
public class UserConverter {

    public UserRegisterCommand toUserRegisterCommand(UserRegisterRequest userRegisterRequest) {
        return UserRegisterCommand.builder()
                .fullName(userRegisterRequest.fullName())
                .username(userRegisterRequest.username())
                .email(userRegisterRequest.email())
                .password(userRegisterRequest.password())
                .roles(userRegisterRequest.roles())
                .build();
    }

}
