package vn.sunvibe.auth.core.presentation.converter.user;

import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.user.EmailRegisterCommand;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.presentation.dto.request.user.EmailRegisterRequest;
import vn.sunvibe.auth.core.presentation.dto.request.user.UserRegisterRequest;

/**
 * Converts user requests to commands.
 */
@Component
public class UserConverter {

    public EmailRegisterCommand toEmailRegisterCommand(EmailRegisterRequest request, ClientInfo clientInfo) {
        return new EmailRegisterCommand(request.email(), request.registrationToken(), request.fullName(),
                request.password(), clientInfo);
    }

    public UserRegisterCommand toUserRegisterCommand(UserRegisterRequest userRegisterRequest, ClientInfo clientInfo) {
        return UserRegisterCommand.builder()
                .fullName(userRegisterRequest.fullName())
                .username(userRegisterRequest.username())
                .password(userRegisterRequest.password())
                .idCardNumber(userRegisterRequest.idCardNumber())
                .registrationToken(userRegisterRequest.registrationToken())
                .clientInfo(clientInfo)
                .build();
    }
}
