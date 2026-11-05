package vn.sunvibe.auth.core.application.query.auth;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.application.shared.RequiredAction;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthResultTest {

    @Test
    void of_user_carriesUserIdAndFullName() {
        AppUser user = AuthTestUsers.withPhone(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A",
                "0912345678", null, Password.create("hashed"), ProfileImage.create(null), null,
                new HashSet<>(), Instant.now());

        AuthResult result = AuthResult.of(user, "access-token", "refresh-secret", RequiredAction.BIND_EMAIL);

        assertEquals("01ARZ3NDEKTSV4RRFFQ69G5FAV", result.userId());
        assertEquals("Nguyen Van A", result.fullName());
        assertEquals("0912345678", result.username());
        assertNull(result.email());
        assertEquals(RequiredAction.BIND_EMAIL, result.requiredAction());
    }

    /** Both tokens stay out of logs: toString never prints them. */
    @Test
    void toString_withTokens_hidesAccessAndRefreshToken() {
        AuthResult result = new AuthResult("access-token-value", "refresh-secret-value", "0912345678", null,
                Set.of("CUSTOMER"), true, false, "01ARZ3NDEKTSV4RRFFQ69G5FAV", "Nguyen Van A", null);

        String text = result.toString();

        assertFalse(text.contains("access-token-value"), text);
        assertFalse(text.contains("refresh-secret-value"), text);
        assertFalse(text.contains("0912345678"), text);
    }
}
