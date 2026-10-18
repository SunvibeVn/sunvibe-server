package vn.sunvibe.auth.core.application.usecase.staff;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The accounts an owner lead may be given to (acquisition-owner-lead-queue spec 5.4), and the names of staff
 * accounts. Published for other modules: the app module adapts it to acquisition-service's lead assignee port,
 * so the modules do not depend on each other.
 * <p>
 * Callable only by acquisition's already-authorized operations. The eligible-assignee list reaches HTTP only
 * through acquisition's assignee endpoint; the display names are for ids read from acquisition rows the caller
 * may already see and are never exposed over HTTP by themselves.
 */
public interface LeadAssigneeAccountUseCase {

    /**
     * True if the account exists (not soft-deleted), may sign in (not locked, terminated or deleted) and holds the
     * SALE or SALE_LEADER role. An unknown, soft-deleted, blank or null id gives false and never throws.
     */
    boolean isEligibleOwnerLeadAssignee(String userId);

    /**
     * Every account for which {@link #isEligibleOwnerLeadAssignee} is true, once each with its SALE and/or
     * SALE_LEADER roles (the roles set is ordered SALE, SALE_LEADER), sorted by full name (Vietnamese collation,
     * accents ignored) then user id. At most 500 accounts: the first 500 in that order.
     */
    List<LeadAssigneeAccount> listEligibleOwnerLeadAssignees();

    /**
     * The full name of each account with one of these ids, including soft-deleted, locked and role-less accounts
     * (acquisition-owner-lead-queue-ui C-35: history keeps the name of staff who left). Unknown, null and blank ids
     * are absent from the result. Empty input makes no query. Only for ids read from acquisition rows the caller
     * may see; never exposed over HTTP by itself.
     */
    Map<String, String> findStaffDisplayNames(Collection<String> userIds);
}
