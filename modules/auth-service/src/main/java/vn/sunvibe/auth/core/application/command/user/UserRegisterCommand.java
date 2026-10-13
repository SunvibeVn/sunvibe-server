package vn.sunvibe.auth.core.application.command.user;

import lombok.Builder;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;

/**
 * Self-registration input. {@code fullName}, {@code username} and {@code password}
 * are required; {@code email} and {@code idCardNumber} are optional (null or blank
 * means "not provided") and can be added to the profile later.
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
        String email,
        String password,
        String idCardNumber,
        String registrationToken,
        ClientInfo clientInfo
) {
}
