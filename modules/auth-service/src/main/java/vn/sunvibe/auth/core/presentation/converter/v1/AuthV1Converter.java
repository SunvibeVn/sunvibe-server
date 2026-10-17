package vn.sunvibe.auth.core.presentation.converter.v1;

import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.query.auth.PasswordForgotResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.query.user.UserProfileResult;
import vn.sunvibe.auth.core.presentation.dto.response.v1.CodeSentResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.MeResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.PasswordResetStartResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.SessionResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.SessionUserResponse;

import java.time.Duration;
import java.util.List;

/**
 * Converts use-case results to the bodies of the {@code /api/v1/auth} endpoints (no wrapper, no message).
 */
@Component
public class AuthV1Converter {

    /** Value of {@link SessionResponse#tokenType()}. */
    static final String TOKEN_TYPE = "Bearer";

    /**
     * Body of every v1 response that starts or refreshes a session: roles sorted by name, and never the
     * refresh token (auth-browser-session BR-4: it travels only in its HttpOnly cookie).
     */
    public SessionResponse toSessionResponse(AuthResult result, Duration accessTokenTtl) {
        List<String> roles = result.roles() == null ? List.of() : result.roles().stream().sorted().toList();
        SessionUserResponse user = new SessionUserResponse(result.userId(), result.username(), result.fullName(),
                result.email(), roles, result.phoneVerified(), result.emailVerified());
        return new SessionResponse(TOKEN_TYPE, result.accessToken(), accessTokenTtl.toSeconds(), user);
    }

    public MeResponse toMeResponse(UserProfileResult profile) {
        return new MeResponse(profile.fullName(), profile.username(), profile.email(), profile.profileImageUrl(),
                profile.kycStatus(), profile.accountStatus(), profile.phoneVerified(), profile.emailVerified());
    }

    public CodeSentResponse toCodeSentResponse(VerificationSendResult result) {
        return new CodeSentResponse(result.channel().name(), result.maskedDestination(), result.expiresAt(),
                result.resendAvailableAt());
    }

    public PasswordResetStartResponse toPasswordResetStartResponse(PasswordForgotResult result) {
        return new PasswordResetStartResponse(result.resetToken(), result.expiresAt(),
                result.codeLifetime().toMinutes());
    }
}
