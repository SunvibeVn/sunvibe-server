package vn.sunvibe.auth.core.presentation.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationPhoneVerificationUseCase;
import vn.sunvibe.auth.core.presentation.dto.request.auth.RegistrationPhoneSendRequest;
import vn.sunvibe.auth.core.presentation.dto.request.auth.RegistrationPhoneVerifyRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.RegistrationTokenResponse;
import vn.sunvibe.auth.core.presentation.dto.response.auth.VerificationSendResponse;
import vn.sunvibe.shared.response.ApiResponse;

/**
 * Phone verification before registration ("verify first, register later"). Both
 * endpoints are public: the person has no account yet.
 */
@RestController
@RequestMapping("/api/auth/registration/phone")
@RequiredArgsConstructor
public class RegistrationVerificationController {

    private final RegistrationPhoneVerificationUseCase registrationPhoneVerificationUseCase;

    @PostMapping(value = "/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<VerificationSendResponse>> send(
            @Valid @RequestBody RegistrationPhoneSendRequest request) {
        VerificationSendResult result = registrationPhoneVerificationUseCase.sendCode(request.phoneNumber());
        VerificationSendResponse response = new VerificationSendResponse(result.channel().name(),
                result.maskedDestination(), result.expiresAt(), result.resendAvailableAt());
        return ResponseEntity.ok(ApiResponse.<VerificationSendResponse>builder()
                .code(HttpStatus.OK.value()).message("Verification code sent by SMS.").result(response).build());
    }

    @PostMapping(value = "/verify", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<RegistrationTokenResponse>> verify(
            @Valid @RequestBody RegistrationPhoneVerifyRequest request) {
        RegistrationTokenResult result = registrationPhoneVerificationUseCase.verifyCode(request.phoneNumber(),
                request.code());
        RegistrationTokenResponse response = new RegistrationTokenResponse(result.registrationToken(),
                result.expiresAt());
        return ResponseEntity.ok(ApiResponse.<RegistrationTokenResponse>builder()
                .code(HttpStatus.OK.value()).message("Phone number verified.").result(response).build());
    }
}
