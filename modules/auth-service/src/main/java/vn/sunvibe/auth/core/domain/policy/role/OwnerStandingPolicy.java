package vn.sunvibe.auth.core.domain.policy.role;

import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.AccountStanding;

import java.util.Set;

/**
 * Whether an account may own a property now, decided from its stored columns (auth-roles BR-8, property-create intake
 * D5-10). The owner standing of one account and of a set of accounts both ask this policy, so the rule has one place.
 */
public final class OwnerStandingPolicy {

    private OwnerStandingPolicy() {
    }

    /**
     * auth-roles BR-8: an owner must be able to sign in, so a soft-deleted account and a status that blocks sign-in
     * (locked, terminated, deleted) are refused; a pending account may still sign in.
     */
    public static boolean mayAuthenticate(AccountStanding standing) {
        return standing.deletedAt() == null
                && !AppUser.SIGN_IN_BLOCKED_STATUSES.contains(UserStatus.valueOf(standing.userStatus()));
    }

    /** auth-roles BR-8, property-create intake D5-10: an account that holds any staff or admin role cannot own. */
    public static boolean holdsStaffRole(Set<String> roleNames) {
        return RoleName.STAFF.stream().anyMatch(role -> roleNames.contains(role.name()));
    }
}
