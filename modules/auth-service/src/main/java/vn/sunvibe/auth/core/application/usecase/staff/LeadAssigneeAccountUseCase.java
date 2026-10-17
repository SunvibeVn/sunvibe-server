package vn.sunvibe.auth.core.application.usecase.staff;

/**
 * The account an owner lead may be given to (acquisition-owner-lead-queue spec 5.4). Published for other modules:
 * the app module adapts it to acquisition-service's lead assignee port, so the modules do not depend on each other.
 * Callable only by acquisition's already-authorized operations; it is never exposed over HTTP.
 */
public interface LeadAssigneeAccountUseCase {

    /**
     * True if the account exists (not soft-deleted), may sign in (not locked, terminated or deleted) and holds the
     * SALE or SALE_LEADER role. An unknown, soft-deleted, blank or null id gives false and never throws.
     */
    boolean isEligibleOwnerLeadAssignee(String userId);
}
