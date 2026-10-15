package vn.sunvibe.auth.core.application.command.role;

/**
 * Grant or revoke one role (role admin API).
 *
 * @param actorUserId       the signed-in staff member
 * @param actorIsSuperAdmin whether the actor holds SUPER_ADMIN (only SUPER_ADMIN may change ADMIN)
 * @param targetUserId      the account to change
 * @param role              the role name as sent by the client; validated by the use case
 */
public record RoleChangeCommand(String actorUserId, boolean actorIsSuperAdmin, String targetUserId, String role) {
}
