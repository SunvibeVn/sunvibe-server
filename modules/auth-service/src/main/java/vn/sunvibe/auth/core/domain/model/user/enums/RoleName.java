package vn.sunvibe.auth.core.domain.model.user.enums;

/**
 * Roles a user can hold; granted to Spring Security as {@code ROLE_<name>}.
 * Self-registration always grants {@link #TENANT}; ADMIN and SUPER_ADMIN can
 * never be self-assigned.
 */
public enum RoleName {

    SUPER_ADMIN,
    ADMIN,
    MANAGER,
    LANDLORD,
    TENANT,
    LEADER,
    /** Common role for every employee. */
    EMPLOYEE,
    /** Common role for every user. */
    USER

}
