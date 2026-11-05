package vn.sunvibe.auth.core.infrastructure.security.provider;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
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

    private final JWTProvider provider = new JWTProvider(SECRET, 60);

    private static AppUser user(UserStatus status, Email email) {
        AppUser user = AuthTestUsers.withPhone(UserId.create("id"), "Nguyen Van A", "0912345678", email,
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

    /** auth-email-access S-18: nothing reads the claim, and an address has no place in a token. */
    @Test
    void provider_noEmailClaim() {
        Claims claims = claimsOf(provider.issue(user(UserStatus.ACTIVE, Email.create("a@example.com"))));

        assertNull(claims.get("email"));
    }

    /** auth-email-access A-4: users.id is the identity, for an account without a phone too. */
    @Test
    void provider_subjectIsUserId() {
        AppUser user = user(UserStatus.ACTIVE, Email.create("a@example.com"));
        user.verifyPhone(Instant.now());
        user.setCredentialVersion(4);

        Claims claims = claimsOf(provider.issue(user));

        assertEquals("id", claims.getSubject());
        assertEquals("id", claims.get("uid", String.class));
        assertEquals(4L, claims.get(JWTProvider.CREDENTIAL_VERSION_CLAIM, Long.class));
        assertEquals(Set.of("ROLE_TENANT", "PHONE_VERIFIED"), Set.copyOf(claims.get("rol", List.class)));
    }

    @Test
    void provider_accountWithoutPhone_issues() {
        AppUser user = AppUser.registerWithEmail(UserId.create("id"), "Nguyen Van A", Email.create("a@example.com"),
                Password.create("hashed"), new HashSet<>(Set.of("TENANT")), Instant.now());

        assertEquals("id", claimsOf(provider.issue(user)).getSubject());
    }

    @Test
    void validateTokenAndGetJws_rejectsTokenSignedWithAnotherKey() {
        JWTProvider other = new JWTProvider("HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw=", 60);

        assertTrue(provider.validateTokenAndGetJws(other.issue(user(UserStatus.ACTIVE, null))).isEmpty());
    }
}
