package vn.sunvibe.auth.core.presentation.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.application.usecase.user.UserRegisterUseCase;
import vn.sunvibe.auth.core.presentation.converter.auth.AuthConverter;
import vn.sunvibe.auth.core.presentation.converter.user.UserConverter;
import vn.sunvibe.auth.core.presentation.dto.request.user.UserRegisterRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.common.response.ApiResponse;
import vn.sunvibe.auth.core.shared.constant.UserMessage;
import vn.sunvibe.auth.core.shared.utils.http.HttpRequestUtils;

@RestController
@RequestMapping("/api/users/command")
@RequiredArgsConstructor
public class UserCommandController {

    private final UserRegisterUseCase userRegisterUseCase;
    private final LoginUseCase loginUseCase;
    private final UserConverter userConverter;
    private final AuthConverter authConverter;

    @PostMapping(value = "/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@Valid @RequestBody UserRegisterRequest userRegisterRequest, HttpServletRequest httpRequest) {
        // Get fingerprint to create refresh token
        String rawFingerprint = HttpRequestUtils.getRawFingerprint(httpRequest);
        // Register user
        UserRegisterCommand userRegisterCommand = userConverter.toUserRegisterCommand(userRegisterRequest);
        userRegisterUseCase.execute(userRegisterCommand, rawFingerprint);

        // Login user after register success
        LoginCommand loginCommand = authConverter.toLoginCommand(userRegisterRequest);
        AuthResult result = loginUseCase.execute(loginCommand, rawFingerprint);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, String.valueOf(result.accessCookie())).header(HttpHeaders.SET_COOKIE, String.valueOf(result.refreshCookie()))
                .body((ApiResponse.<LoginResponse>builder().code(HttpStatus.CREATED.value()).message(UserMessage.USER_CREATE_SUCCESS).result(result.loginResponse()).build()));
    }

}
