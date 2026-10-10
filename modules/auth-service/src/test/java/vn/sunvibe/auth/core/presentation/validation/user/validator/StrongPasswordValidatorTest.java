package vn.sunvibe.auth.core.presentation.validation.user.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.presentation.dto.request.user.UserRegisterRequest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StrongPasswordValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<UserRegisterRequest>> violationsFor(String password) {
        UserRegisterRequest request = UserRegisterRequest.builder().fullName("Nguyen Van A").username("0912345678")
                .password(password).confirmPassword(password).build();
        return validator.validate(request);
    }

    private static boolean onPassword(Set<ConstraintViolation<UserRegisterRequest>> violations) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void acceptsAStrongPassword() {
        assertTrue(violationsFor("Str0ng@Pass").isEmpty());
    }

    @Test
    void acceptsExactly72Characters() {
        assertTrue(violationsFor("Aa1@" + "x".repeat(68)).isEmpty());
    }

    /** BCrypt cannot hash more than 72 bytes: longer input must be a 4xx, not a 500 at hashing time. */
    @Test
    void rejectsMoreThan72Characters() {
        Set<ConstraintViolation<UserRegisterRequest>> violations = violationsFor("Aa1@" + "x".repeat(69));

        assertTrue(onPassword(violations));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("at most 72")));
    }

    @Test
    void rejectsShortOrWeakPasswords() {
        assertTrue(onPassword(violationsFor("Aa1@")));
        assertTrue(onPassword(violationsFor("alllowercase1@")));
        assertTrue(onPassword(violationsFor("NoSpecial123")));
    }
}
