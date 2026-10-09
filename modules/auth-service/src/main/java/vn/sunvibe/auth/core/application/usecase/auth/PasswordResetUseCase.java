package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordResetResponse;

public interface PasswordResetUseCase {

    PasswordResetResponse verifyOtpAndResetPassword(PasswordResetRequest request);

}