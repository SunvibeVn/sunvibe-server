package vn.sunvibe.auth.core.presentation.dto.response.v1;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The signed-in account as it is at this moment, inside a {@link SessionResponse}.
 * {@code email} is always present in the JSON and is null when the account has no e-mail.
 */
@Schema(description = "The signed-in account as it is at this moment")
public record SessionUserResponse(
        @Schema(description = "Opaque ULID of the account", example = "01JAXXXXXXXXXXXXXXXXXXXXXX")
        String userId,
        @Schema(description = "The account's phone number; null on an account signed up by email", example = "0901234567",
                nullable = true)
        String username,
        @Schema(description = "Display name of the account")
        String fullName,
        @Schema(description = "E-mail of the account; null when it has none", nullable = true)
        String email,
        @Schema(description = "Role names, sorted, e.g. CUSTOMER, TENANT")
        List<String> roles,
        @Schema(description = "False right after registration: show the enter-the-SMS-code screen")
        boolean phoneVerified,
        @Schema(description = "False when there is no e-mail or it is not confirmed yet")
        boolean emailVerified,
        @Schema(description = "What the account must do next; null when nothing", nullable = true,
                allowableValues = {"BIND_EMAIL", "VERIFY_PHONE"})
        String requiredAction) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "SessionUserResponse[userId=" + userId + ", username=[hidden], email=[hidden], roles=" + roles + "]";
    }
}
