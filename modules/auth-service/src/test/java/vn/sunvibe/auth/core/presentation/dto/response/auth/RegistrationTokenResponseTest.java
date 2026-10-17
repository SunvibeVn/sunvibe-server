package vn.sunvibe.auth.core.presentation.dto.response.auth;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationTokenResponseTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-10-17T10:15:00Z");

    /** The registration token stays out of logs: toString never prints it. */
    @Test
    void toString_withToken_hidesRegistrationToken() {
        String text = new RegistrationTokenResponse("registration-token-value", EXPIRES_AT).toString();

        assertFalse(text.contains("registration-token-value"), text);
        assertTrue(text.contains(EXPIRES_AT.toString()), text);
    }

    /** The JSON body is unchanged: hiding the token from toString does not touch serialization. */
    @Test
    void json_withToken_keepsTokenInBody() {
        String json = JsonMapper.builder().build()
                .writeValueAsString(new RegistrationTokenResponse("registration-token-value", EXPIRES_AT));

        assertEquals("registration-token-value",
                JsonMapper.builder().build().readTree(json).path("registrationToken").asString());
    }
}
