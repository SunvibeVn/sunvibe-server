package vn.sunvibe.auth.core.application.command.user;

import lombok.Builder;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;

/**
 * Self-registration by phone. {@code fullName}, {@code username} and {@code password}
 * are required; {@code idCardNumber} is optional (null or blank means "not provided").
 * No email is taken: an unproven address could occupy someone else's mailbox
 * (auth-email-access D4-11).
 *
 * @param registrationToken proof that the phone number was verified first (see
 *                          {@code RegistrationPhoneVerificationUseCase}); required unless the
 *                          phone-verification requirement is switched off
 * @param clientInfo the device that gets the first session; {@link ClientInfo#unknown()} when null
 */
@Builder
public record UserRegisterCommand(
        String fullName,
        String username,
        String password,
        String idCardNumber,
        String registrationToken,
        ClientInfo clientInfo
) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "UserRegisterCommand[hidden]";
    }
}
