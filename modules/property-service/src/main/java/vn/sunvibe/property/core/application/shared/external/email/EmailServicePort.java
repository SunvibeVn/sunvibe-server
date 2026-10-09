package vn.sunvibe.property.core.application.shared.external.email;

public interface EmailServicePort {
    void sendPasswordResetEmail(String email, String otpCode, String resetToken);
    void sendPasswordResetConfirmationEmail(String email);
}
