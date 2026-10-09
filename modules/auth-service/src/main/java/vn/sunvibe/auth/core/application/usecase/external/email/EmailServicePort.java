package vn.sunvibe.auth.core.application.usecase.external.email;

public interface EmailServicePort {
    void sendPasswordResetEmail(String email, String otpCode, String resetToken);
    void sendPasswordResetConfirmationEmail(String email);

    /** Sends the 6-digit code that proves the user controls this address. */
    void sendVerificationCodeEmail(String email, String code);
}
