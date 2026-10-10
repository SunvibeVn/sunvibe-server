package vn.sunvibe.auth.core.infrastructure.security.provider;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JWTProviderTest {

    private static final String SECRET = "bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=";

    private final JWTProvider provider = new JWTProvider(SECRET, 60, "cookie-jwt");

    private static AppUser user(UserStatus status, Email email) {
        AppUser user = AppUser.create(UserId.create("id"), "Nguyen Van A", Username.create("0912345678"), email,
                Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(Set.of("TENANT")),
                Instant.now());
        user.setUserStatus(status);
        return user;
    }

    private Claims claimsOf(String token) {
        return provider.validateTokenAndGetJws(token).orElseThrow().getPayload();
    }

    @Test
    void issue_rejectsLockedUser() {
        assertThrows(LockedException.class, () -> provider.issue(user(UserStatus.LOCK, null)));
    }

    @Test
    void issue_rejectsTerminatedUser() {
        assertThrows(DisabledException.class, () -> provider.issue(user(UserStatus.TERMINATED, null)));
    }

    @Test
    void issue_rejectsDeletedUser() {
        assertThrows(DisabledException.class, () -> provider.issue(user(UserStatus.DELETED, null)));
    }

    @Test
    void issue_userWithoutEmail_omitsEmailClaim() {
        String token = provider.issue(user(UserStatus.ACTIVE, null));

        assertNull(claimsOf(token).get("email"));
        assertEquals("id", claimsOf(token).get("uid", String.class));
    }

    @Test
    void issue_carriesUserIdUsernameAndRoles() {
        AppUser user = user(UserStatus.ACTIVE, Email.create("a@example.com"));
        user.verifyPhone(Instant.now());

        Claims claims = claimsOf(provider.issue(user));

        assertEquals("id", claims.get("uid", String.class));
        assertEquals("0912345678", claims.getSubject());
        assertEquals("a@example.com", claims.get("email", String.class));
        assertEquals(Set.of("ROLE_TENANT", "PHONE_VERIFIED"), Set.copyOf(claims.get("rol", List.class)));
    }

    @Test
    void validateTokenAndGetJws_rejectsTokenSignedWithAnotherKey() {
        JWTProvider other = new JWTProvider("HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw=", 60, "cookie-jwt");

        assertTrue(provider.validateTokenAndGetJws(other.issue(user(UserStatus.ACTIVE, null))).isEmpty());
    }
}
