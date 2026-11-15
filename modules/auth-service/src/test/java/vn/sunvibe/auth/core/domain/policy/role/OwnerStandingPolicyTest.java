package vn.sunvibe.auth.core.domain.policy.role;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.repository.user.AccountStanding;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The owner eligibility that ownerStanding and the set read share (auth-roles BR-8, property-create intake D5-10). */
class OwnerStandingPolicyTest {

    private static final Instant T0 = Instant.parse("2026-11-06T02:00:00Z");

    private static AccountStanding standing(String status, Instant deletedAt) {
        return new AccountStanding(status, deletedAt, 0, T0);
    }

    @Test
    void mayAuthenticate_eachStatus_blockedOnesRefused() {
        assertTrue(OwnerStandingPolicy.mayAuthenticate(standing("ACTIVE", null)));
        assertTrue(OwnerStandingPolicy.mayAuthenticate(standing("PENDING", null)));
        for (String blocked : List.of("LOCK", "TERMINATED", "DELETED")) {
            assertFalse(OwnerStandingPolicy.mayAuthenticate(standing(blocked, null)), blocked);
        }
    }

    @Test
    void mayAuthenticate_softDeleted_refused() {
        assertFalse(OwnerStandingPolicy.mayAuthenticate(standing("ACTIVE", T0)));
    }

    @Test
    void holdsStaffRole_eachStaffRole_true() {
        for (RoleName role : RoleName.STAFF) {
            assertTrue(OwnerStandingPolicy.holdsStaffRole(Set.of(role.name(), "CUSTOMER")), role.name());
        }
    }

    @Test
    void holdsStaffRole_customerOrOwnerOnly_false() {
        assertFalse(OwnerStandingPolicy.holdsStaffRole(Set.of()));
        assertFalse(OwnerStandingPolicy.holdsStaffRole(Set.of("CUSTOMER")));
        assertFalse(OwnerStandingPolicy.holdsStaffRole(Set.of("CUSTOMER", "OWNER")));
    }
}
