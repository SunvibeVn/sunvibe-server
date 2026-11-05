package vn.sunvibe.auth.core.presentation.dto.response.v1;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code GET /api/v1/me}: the signed-in user's own profile. */
@Schema(description = "The signed-in user's own profile")
public record MeResponse(
        @Schema(description = "Display name of the account")
        String fullName,
        @Schema(description = "The account's phone number; null on an account signed up by email", example = "0901234567",
                nullable = true)
        String username,
        @Schema(description = "E-mail of the account; null when it has none", nullable = true)
        String email,
        @Schema(description = "URL of the profile image; null when there is none", nullable = true)
        String profileImageUrl,
        @Schema(description = "Identity check status (name of KycStatus)")
        String kycStatus,
        @Schema(description = "Account status (name of UserStatus)")
        String accountStatus,
        @Schema(description = "Whether the phone number is confirmed")
        boolean phoneVerified,
        @Schema(description = "False when there is no e-mail or it is not confirmed yet")
        boolean emailVerified,
        @Schema(description = "What the account must do next; null when nothing", nullable = true,
                allowableValues = {"BIND_EMAIL", "VERIFY_PHONE"})
        String requiredAction) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "MeResponse[hidden]";
    }
}
