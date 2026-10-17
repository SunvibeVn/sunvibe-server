package vn.sunvibe.auth.core.presentation.dto.response.v1;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of every v1 response that starts or refreshes a session. The refresh token is never in it
 * (auth-browser-session BR-4: the refresh token travels only in its HttpOnly cookie).
 */
@Schema(description = "A started or refreshed session: the access token and the account")
public record SessionResponse(
        @Schema(description = "Always Bearer", example = "Bearer")
        String tokenType,
        @Schema(description = "The JWT to send as Authorization: Bearer ...")
        String accessToken,
        @Schema(description = "Seconds until accessToken expires; refresh shortly before", example = "900")
        long expiresIn,
        @Schema(description = "The signed-in account")
        SessionUserResponse user) {

    /** The access token stays out of logs. */
    @Override
    public String toString() {
        return "SessionResponse[tokenType=" + tokenType + ", accessToken=[hidden], expiresIn=" + expiresIn
                + ", user=" + user + "]";
    }
}
