package vn.sunvibe.acquisition.core.application.shared.external.staff;

/**
 * Read-only view of staff accounts, owned by another module. The adapter lives in the app module, so
 * acquisition-service does not depend on auth-service (ADR-009).
 */
public interface LeadAssigneeDirectoryPort {

    /**
     * True if the account exists, may sign in and holds the SALE or SALE_LEADER role, so an owner lead may be given
     * to it. Unknown, deleted or blank ids give false.
     */
    boolean isEligibleAssignee(String userId);
}
