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
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.usecase.auth.ContactVerificationUseCase;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.presentation.dto.request.auth.VerificationConfirmRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.VerificationSendResponse;
import vn.sunvibe.common.response.ApiResponse;

/**
 * Verification of the signed-in user's phone number (SMS code) and e-mail.
 * A new account is PENDING until its phone is verified.
 */
@RestController
@RequestMapping("/api/auth/verification")
@RequiredArgsConstructor
public class ContactVerificationController {

    private final ContactVerificationUseCase contactVerificationUseCase;

    @PostMapping(value = "/phone/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<VerificationSendResponse>> sendPhoneCode() {
        return sent(contactVerificationUseCase.sendCode(VerificationChannel.PHONE), "Verification code sent by SMS.");
    }

    @PostMapping(value = "/phone/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> confirmPhone(@Valid @RequestBody VerificationConfirmRequest request) {
        contactVerificationUseCase.confirm(VerificationChannel.PHONE, request.code());
        return ok("Phone number verified.");
    }

    @PostMapping(value = "/email/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<VerificationSendResponse>> sendEmailCode() {
        return sent(contactVerificationUseCase.sendCode(VerificationChannel.EMAIL), "Verification code sent by email.");
    }

    @PostMapping(value = "/email/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> confirmEmail(@Valid @RequestBody VerificationConfirmRequest request) {
        contactVerificationUseCase.confirm(VerificationChannel.EMAIL, request.code());
        return ok("Email verified.");
    }

    private static ResponseEntity<ApiResponse<VerificationSendResponse>> sent(VerificationSendResult result, String message) {
        VerificationSendResponse response = new VerificationSendResponse(result.channel().name(),
                result.maskedDestination(), result.expiresAt(), result.resendAvailableAt());
        return ResponseEntity.ok(ApiResponse.<VerificationSendResponse>builder()
                .code(HttpStatus.OK.value()).message(message).result(response).build());
    }

    private static ResponseEntity<ApiResponse<Void>> ok(String message) {
        return ResponseEntity.ok(ApiResponse.<Void>builder().code(HttpStatus.OK.value()).message(message).build());
    }
}
