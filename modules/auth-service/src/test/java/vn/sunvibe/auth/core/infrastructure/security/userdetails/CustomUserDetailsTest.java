package vn.sunvibe.auth.core.infrastructure.security.userdetails;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    private CustomUserDetails detailsWithStatus(UserStatus status) {
        CustomUserDetails details = new CustomUserDetails();
        details.setUserId("id");
        details.setUsername("0912345678");
        details.setPassword("hashed");
        details.setEmail("a@example.com");
        details.setAuthorities(Set.of());
        details.setUserStatus(status);
        return details;
    }

    @Test
    void isEnabled_falseWhenDeleted() {
        assertFalse(detailsWithStatus(UserStatus.DELETED).isEnabled());
    }

    @Test
    void isEnabled_falseWhenTerminated() {
        assertFalse(detailsWithStatus(UserStatus.TERMINATED).isEnabled());
    }

    @Test
    void isEnabled_trueWhenActive() {
        assertTrue(detailsWithStatus(UserStatus.ACTIVE).isEnabled());
    }

    @Test
    void isAccountNonLocked_falseWhenLocked() {
        assertFalse(detailsWithStatus(UserStatus.LOCK).isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_trueWhenActive() {
        assertTrue(detailsWithStatus(UserStatus.ACTIVE).isAccountNonLocked());
    }
}
