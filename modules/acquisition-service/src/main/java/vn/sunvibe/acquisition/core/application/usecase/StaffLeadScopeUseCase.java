package vn.sunvibe.acquisition.core.application.usecase;

import java.util.Set;

/**
 * The owner leads a staff member may see, for a list of another module whose rows rest on a lead (brokerage-agreement
 * BR-16: an agreement's readers are those who may see its basis lead). Published for other modules: the app module
 * adapts it to brokerage-service's lead scope port, so the modules do not depend on each other (ADR-009).
 * <p>
 * It answers only the caller's own scope: brokerage's agreement list calls it with the signed-in principal's id and
 * expanded authorities, after its class rule has passed, inside the list's transaction. It is never exposed over HTTP
 * by itself. The mapping from roles to leads is acquisition's one rule (acquisition-owner-lead-queue BR-1), so the
 * lead queue and this answer never disagree.
 */
public interface StaffLeadScopeUseCase {

    /**
     * Every lead for SALE_LEADER, SALES_MANAGER or ADMIN (a person with SALE and a leader role included); for SALE, the
     * ids of every owner lead whose current assignee is the user, in any status, because an agreement outlives its
     * lead's state (brokerage-agreement BR-18); for anyone else, no lead.
     *
     * @param expandedAuthorities the user's authorities, already expanded through the role hierarchy
     */
    StaffLeadScope scopeFor(String userId, Set<String> expandedAuthorities);
}
