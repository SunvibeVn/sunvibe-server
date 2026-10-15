package vn.sunvibe.auth.core.domain.model.user.enums;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Roles a user can hold; granted to Spring Security as {@code ROLE_<name>}.
 * Since wave W6 (auth-roles brief BR-1): the 8 old roles are kept, 6 target roles are added.
 * Self-registration grants exactly {@link #SELF_REGISTRATION} (BR-3).
 */
public enum RoleName {

    SUPER_ADMIN,
    ADMIN,
    MANAGER,
    LANDLORD,
    TENANT,
    /** Team lead outside sales (not {@link #SALE_LEADER}); no rights of its own yet. */
    LEADER,
    /** Common role for every employee. */
    EMPLOYEE,
    /** Common role for every user. */
    USER,

    /** Property owner. */
    OWNER,
    /** Customer (renting or buying). */
    CUSTOMER,
    /** Sunvibe sales staff / broker. */
    SALE,
    /** Sales team lead. */
    SALE_LEADER,
    /** Sales manager. */
    SALES_MANAGER,
    /** Reviews listings (maker-checker). */
    LISTING_REVIEWER;

    /** The roles of a self-registered account (BR-3). */
    public static final Set<RoleName> SELF_REGISTRATION = EnumSet.of(TENANT, CUSTOMER);

    /** Staff and admin roles: an account holding any of them can never be a property owner (BR-7). */
    public static final Set<RoleName> STAFF = EnumSet.of(SALE, SALE_LEADER, SALES_MANAGER, LISTING_REVIEWER,
            ADMIN, SUPER_ADMIN, EMPLOYEE, MANAGER, LEADER);

    /** Roles an ADMIN may grant or revoke through the role admin API (US-4 AC-1). */
    public static final Set<RoleName> GRANTABLE_BY_ADMIN = EnumSet.of(OWNER, CUSTOMER, SALE, SALE_LEADER,
            SALES_MANAGER, LISTING_REVIEWER);

    /** Roles a SUPER_ADMIN may grant or revoke: those of ADMIN plus ADMIN itself (US-4 AC-2). */
    public static final Set<RoleName> GRANTABLE_BY_SUPER_ADMIN;

    static {
        EnumSet<RoleName> bySuperAdmin = EnumSet.copyOf(GRANTABLE_BY_ADMIN);
        bySuperAdmin.add(ADMIN);
        GRANTABLE_BY_SUPER_ADMIN = bySuperAdmin;
    }

    /** The role with exactly this name (case-sensitive), if any. */
    public static Optional<RoleName> parse(String name) {
        for (RoleName role : values()) {
            if (role.name().equals(name)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
