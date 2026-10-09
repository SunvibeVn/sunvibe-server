package vn.sunvibe.auth.core.application.usecase.external.otp;

import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;

public interface OtpServicePort {
    OtpCode generateOtp();
    boolean validateOtp(String code, OtpCode otpCode);
}
