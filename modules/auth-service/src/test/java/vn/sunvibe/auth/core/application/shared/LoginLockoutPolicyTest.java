package vn.sunvibe.auth.core.application.shared;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LoginLockoutPolicyTest {

    private final LoginLockoutPolicy policy = new LoginLockoutPolicy(5, Duration.ofMinutes(15), Duration.ofHours(24));

    @Test
    void noLockBeforeTheLimit() {
        for (int attempts = 1; attempts < 5; attempts++) {
            assertEquals(Optional.empty(), policy.lockDurationAfter(attempts));
        }
    }

    @Test
    void locksOnEveryMultipleOfTheLimitAndDoublesEachRound() {
        assertEquals(Optional.of(Duration.ofMinutes(15)), policy.lockDurationAfter(5));
        assertEquals(Optional.empty(), policy.lockDurationAfter(6));
        assertEquals(Optional.of(Duration.ofMinutes(30)), policy.lockDurationAfter(10));
        assertEquals(Optional.of(Duration.ofHours(1)), policy.lockDurationAfter(15));
    }

    @Test
    void lockNeverExceedsTheMaximum() {
        assertEquals(Optional.of(Duration.ofHours(24)), policy.lockDurationAfter(5 * 30));
        assertEquals(Optional.of(Duration.ofHours(24)), policy.lockDurationAfter(5 * 1000));
    }

    @Test
    void rejectsInconsistentSettings() {
        assertThrows(IllegalArgumentException.class, () -> new LoginLockoutPolicy(0, Duration.ofMinutes(15), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new LoginLockoutPolicy(5, Duration.ofHours(2), Duration.ofHours(1)));
    }
}
