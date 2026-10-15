package vn.sunvibe.auth.core.domain.model.user.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Roles on the account (auth-roles brief BR-1, BR-3, BR-7). */
class AppUserRoleTest {

    private static AppUser user(RoleName... roles) {
        Set<String> names = new HashSet<>();
        for (RoleName role : roles) {
            names.add(role.name());
        }
        return AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", Username.create("0912345678"),
                null, Password.create("hashed"), ProfileImage.create(null), null, names,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** BR-1 (W6-1): 8 old roles kept, 6 new ones added. */
    @Test
    void roleName_hasTheFourteenRolesOfW6() {
        assertEquals(Set.of("SUPER_ADMIN", "ADMIN", "MANAGER", "LANDLORD", "TENANT", "LEADER", "EMPLOYEE", "USER",
                        "OWNER", "CUSTOMER", "SALE", "SALE_LEADER", "SALES_MANAGER", "LISTING_REVIEWER"),
                Set.copyOf(java.util.Arrays.stream(RoleName.values()).map(Enum::name).toList()));
    }

    /** BR-3 (W6-3): self-registration may only assign TENANT and CUSTOMER. */
    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"TENANT", "CUSTOMER"})
    void assignRole_selfRegistrationRole_isAdded(RoleName role) {
        AppUser user = user();

        user.assignRole(role.name());

        assertEquals(Set.of(role.name()), user.getUserRoles());
    }

    /** BR-3 (W6-3): every other role is refused at self-registration. */
    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"TENANT", "CUSTOMER"}, mode = EnumSource.Mode.EXCLUDE)
    void assignRole_anyOtherRole_isRejected(RoleName role) {
        AppUser user = user();

        assertThrows(IllegalArgumentException.class, () -> user.assignRole(role.name()));
        assertTrue(user.getUserRoles().isEmpty());
    }

    @Test
    void grantRole_missingRole_addsItAndReportsAChange() {
        AppUser user = user(RoleName.CUSTOMER);

        assertTrue(user.grantRole(RoleName.OWNER));
        assertEquals(Set.of("CUSTOMER", "OWNER"), user.getUserRoles());
    }

    @Test
    void grantRole_roleAlreadyHeld_changesNothing() {
        AppUser user = user(RoleName.OWNER);

        assertFalse(user.grantRole(RoleName.OWNER));
        assertEquals(Set.of("OWNER"), user.getUserRoles());
    }

    /** SUPER_ADMIN is never granted by the application (W6-9): only data changes create it. */
    @Test
    void grantRole_superAdmin_isRejected() {
        AppUser user = user(RoleName.ADMIN);

        assertThrows(IllegalArgumentException.class, () -> user.grantRole(RoleName.SUPER_ADMIN));
        assertEquals(Set.of("ADMIN"), user.getUserRoles());
    }

    @Test
    void revokeRole_heldRole_removesItAndReportsAChange() {
        AppUser user = user(RoleName.SALE, RoleName.CUSTOMER);

        assertTrue(user.revokeRole(RoleName.SALE));
        assertEquals(Set.of("CUSTOMER"), user.getUserRoles());
    }

    /** Q-7 default: an account may end with no roles. */
    @Test
    void revokeRole_lastRole_leavesNoRoles() {
        AppUser user = user(RoleName.OWNER);

        assertTrue(user.revokeRole(RoleName.OWNER));
        assertTrue(user.getUserRoles().isEmpty());
    }

    @Test
    void revokeRole_roleNotHeld_changesNothing() {
        AppUser user = user(RoleName.CUSTOMER);

        assertFalse(user.revokeRole(RoleName.SALE));
    }

    /** BR-7 (W6-7): the staff/admin group. */
    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"SALE", "SALE_LEADER", "SALES_MANAGER", "LISTING_REVIEWER", "ADMIN",
            "SUPER_ADMIN", "EMPLOYEE", "MANAGER", "LEADER"})
    void isStaff_staffOrAdminRole_isTrue(RoleName role) {
        assertTrue(user(RoleName.CUSTOMER, role).isStaff());
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"OWNER", "CUSTOMER", "LANDLORD", "TENANT", "USER"})
    void isStaff_nonStaffRole_isFalse(RoleName role) {
        assertFalse(user(role).isStaff());
    }
}
