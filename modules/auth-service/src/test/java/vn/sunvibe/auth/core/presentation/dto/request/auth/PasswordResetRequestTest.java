package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetRequestTest {

    /** Any framework debug line can print a request. */
    @Test
    void toString_hidesTheProofsAndThePassword() {
        String text = PasswordResetRequest.builder().resetToken("reset-token-value").otpCode("123456").newPassword("New@Passw0rd")
                .confirmPassword("New@Passw0rd").build().toString();

        assertFalse(text.contains("reset-token-value"), text);
        assertFalse(text.contains("123456"), text);
        assertFalse(text.contains("New@Passw0rd"), text);
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** Regression: without a no-args constructor every reset request failed with a 500. */
    @Test
    void readsFromJson() {
        PasswordResetRequest request = JsonMapper.builder().build().readValue("""
                {"resetToken":"t","otpCode":"123456","newPassword":"N3w@Passw0rd","confirmPassword":"N3w@Passw0rd"}
                """, PasswordResetRequest.class);

        assertEquals("t", request.getResetToken());
        assertEquals("123456", request.getOtpCode());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsAWeakNewPassword() {
        PasswordResetRequest request = PasswordResetRequest.builder().resetToken("t").otpCode("123456")
                .newPassword("weakpass").confirmPassword("weakpass").build();

        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("newPassword")));
    }
}
