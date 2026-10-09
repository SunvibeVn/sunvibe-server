package vn.sunvibe.auth.core.application.usecase.external.email;

public interface EmailServicePort {
    void sendPasswordResetEmail(String email, String otpCode, String resetToken);
    void sendPasswordResetConfirmationEmail(String email);
}
