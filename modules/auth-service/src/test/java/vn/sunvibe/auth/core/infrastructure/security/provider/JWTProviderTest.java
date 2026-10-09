package vn.sunvibe.auth.core.infrastructure.security.provider;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JWTProviderTest {

    private JWTProvider providerFor(UserStatus status) {
        CustomUserDetails details = new CustomUserDetails();
        details.setUserId("id");
        details.setUsername("0912345678");
        details.setPassword("hashed");
        details.setEmail("a@example.com");
        details.setAuthorities(Set.of());
        details.setUserStatus(status);
        return new JWTProvider(username -> details);
    }

    @Test
    void generateAccessToken_rejectsLockedUser() {
        assertThrows(LockedException.class,
                () -> providerFor(UserStatus.LOCK).generateAccessToken("0912345678"));
    }

    @Test
    void generateAccessToken_rejectsTerminatedUser() {
        assertThrows(DisabledException.class,
                () -> providerFor(UserStatus.TERMINATED).generateAccessToken("0912345678"));
    }

    @Test
    void generateAccessToken_rejectsDeletedUser() {
        assertThrows(DisabledException.class,
                () -> providerFor(UserStatus.DELETED).generateAccessToken("0912345678"));
    }

    @Test
    void generateAccessToken_carriesUserIdClaim() {
        JWTProvider provider = providerFor(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(provider, "jwtSecretKey", "bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=");
        ReflectionTestUtils.setField(provider, "jwtExpirationMinutes", 60L);

        String token = provider.generateAccessToken("0912345678");

        assertEquals("id", provider.extractAllClaims(token).get("uid", String.class));
    }
}
