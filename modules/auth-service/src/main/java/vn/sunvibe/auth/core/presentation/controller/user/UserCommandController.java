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
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.user.UserRegisterUseCase;
import vn.sunvibe.auth.core.presentation.converter.auth.AuthConverter;
import vn.sunvibe.auth.core.presentation.converter.user.UserConverter;
import vn.sunvibe.auth.core.presentation.dto.request.user.UserRegisterRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.auth.core.shared.constant.UserMessage;
import vn.sunvibe.auth.core.shared.service.CookieService;
import vn.sunvibe.shared.response.ApiResponse;

/**
 * Account write operations.
 */
@RestController
@RequestMapping("/api/users/command")
@RequiredArgsConstructor
public class UserCommandController {

    private final UserRegisterUseCase userRegisterUseCase;
    private final UserConverter userConverter;
    private final AuthConverter authConverter;
    private final CookieService cookieService;

    /**
     * Self-registration. The new account is PENDING until the phone is verified;
     * the user is signed in on this device right away (token cookies + body).
     */
    @PostMapping(value = "/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@Valid @RequestBody UserRegisterRequest userRegisterRequest, HttpServletRequest httpRequest) {
        AuthResult result = userRegisterUseCase.execute(
                userConverter.toUserRegisterCommand(userRegisterRequest, authConverter.toClientInfo(httpRequest)));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.buildAccessTokenCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(ApiResponse.<LoginResponse>builder().code(HttpStatus.CREATED.value()).message(UserMessage.USER_CREATE_SUCCESS)
                        .result(authConverter.toLoginResponse(result, UserMessage.USER_CREATE_SUCCESS)).build());
    }

}
