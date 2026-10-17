package vn.sunvibe.auth.core.presentation.converter.v1;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.query.auth.PasswordForgotResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.query.user.UserProfileResult;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.presentation.dto.response.v1.CodeSentResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.MeResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.PasswordResetStartResponse;
import vn.sunvibe.auth.core.presentation.dto.response.v1.SessionResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthV1ConverterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-17T10:15:00Z");

    private static final AuthResult RESULT = new AuthResult("access-token", "refresh-secret", "0912345678", null,
            Set.of("TENANT", "CUSTOMER"), true, false, "01ARZ3NDEKTSV4RRFFQ69G5FAV", "Nguyen Van A");

    private final AuthV1Converter converter = new AuthV1Converter();

    /** W10-2: Bearer, seconds until expiry, the account summary with roles sorted by name. */
    @Test
    void toSessionResponse_fillsBearer900AndSortedRoles() {
        SessionResponse response = converter.toSessionResponse(RESULT, ACCESS_TTL);

        assertEquals("Bearer", response.tokenType());
        assertEquals("access-token", response.accessToken());
        assertEquals(900, response.expiresIn());
        assertEquals("01ARZ3NDEKTSV4RRFFQ69G5FAV", response.user().userId());
        assertEquals("0912345678", response.user().username());
        assertEquals("Nguyen Van A", response.user().fullName());
        assertNull(response.user().email());
        assertEquals(List.of("CUSTOMER", "TENANT"), response.user().roles());
        assertTrue(response.user().phoneVerified());
        assertFalse(response.user().emailVerified());
    }

    /** auth-browser-session BR-4: the refresh token never travels in a body; a missing e-mail is an explicit null. */
    @Test
    void toSessionResponse_jsonNeverContainsTheRefreshToken() {
        String json = JSON.writeValueAsString(converter.toSessionResponse(RESULT, ACCESS_TTL));

        assertFalse(json.contains("refresh-secret"));
        assertFalse(json.contains("refreshToken"));
        assertFalse(json.contains("message"));
        JsonNode user = JSON.readTree(json).get("user");
        assertTrue(user.has("email"));
        assertTrue(user.get("email").isNull());
    }

    /** W10-4: e-mail flow has no reset token in the body and no message field. */
    @Test
    void toPasswordResetStartResponse_emailFlow_hasNoResetTokenAndNoMessageKey() {
        PasswordForgotResult result = new PasswordForgotResult("generic text", null, EXPIRES_AT,
                Duration.ofMinutes(5));

        PasswordResetStartResponse response = converter.toPasswordResetStartResponse(result);
        JsonNode json = JSON.readTree(JSON.writeValueAsString(response));

        assertFalse(json.has("resetToken"));
        assertFalse(json.has("message"));
        assertEquals(EXPIRES_AT, response.expiresAt());
        assertEquals(5, json.get("otpExpirationMinutes").asLong());
        assertTrue(json.has("expiresAt"));
    }

    @Test
    void toPasswordResetStartResponse_smsFlow_carriesTheResetToken() {
        PasswordForgotResult result = new PasswordForgotResult("generic text", "reset-token", EXPIRES_AT,
                Duration.ofMinutes(5));

        JsonNode json = JSON.readTree(JSON.writeValueAsString(converter.toPasswordResetStartResponse(result)));

        assertEquals("reset-token", json.get("resetToken").asString());
        assertFalse(json.has("message"));
    }

    @Test
    void toCodeSentResponse_channelName_andMaskedDestination() {
        Instant resend = EXPIRES_AT.minusSeconds(240);
        VerificationSendResult result = new VerificationSendResult(VerificationChannel.PHONE, "091****678",
                EXPIRES_AT, resend);

        CodeSentResponse response = converter.toCodeSentResponse(result);

        assertEquals("PHONE", response.channel());
        assertEquals("091****678", response.sentTo());
        assertEquals(EXPIRES_AT, response.expiresAt());
        assertEquals(resend, response.resendAvailableAt());
    }

    @Test
    void toMeResponse_copiesEveryProfileField() {
        UserProfileResult profile = new UserProfileResult("Nguyen Van A", "0912345678", "a@example.com",
                "https://img/a.png", "VERIFIED", "ACTIVE", true, true);

        MeResponse response = converter.toMeResponse(profile);

        assertEquals(new MeResponse("Nguyen Van A", "0912345678", "a@example.com", "https://img/a.png",
                "VERIFIED", "ACTIVE", true, true), response);
    }
}
