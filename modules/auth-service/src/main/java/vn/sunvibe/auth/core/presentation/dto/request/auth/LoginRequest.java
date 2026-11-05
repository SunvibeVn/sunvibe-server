package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ExactlyOneLoginIdentifier;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidEmail;

/**
 * Body of {@code POST /api/v1/auth/login}: {@code email}, or {@code username}, which is the phone.
 */
@ExactlyOneLoginIdentifier
public record LoginRequest(

        @ValidEmail
        String email,

        @Size(max = 32, message = "At most 32 characters in length.")
        String username,

        // An empty password skips bcrypt in the encoder, so it would show which accounts exist by their failed-attempt write
        @NotNull(message = "Password is mandatory.")
        @Size(min = 1, max = 256, message = "From 1 to 256 characters in length.")
        String password

) {

    /** The password and the identifiers stay out of logs. */
    @Override
    public String toString() {
        return "LoginRequest[hidden]";
    }
}
