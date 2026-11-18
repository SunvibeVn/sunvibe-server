package vn.sunvibe.brokerage.core.application.shared.external.lead;

import java.util.Set;

/**
 * The owner leads the caller may see, owned by acquisition-service, for the agreement list (E05-S7 spec S7-D2;
 * brokerage-agreement BR-16: an agreement's readers are those who may see its basis lead). The adapter lives in the app
 * module (ADR-009) and joins the caller's transaction, so the list reads the scope inside its own transaction and a
 * reassignment committed before the read shows at once (E-10). It answers only the caller's own scope.
 */
public interface BasisLeadScopePort {

    /**
     * Every lead, or the ids of the leads assigned to the user now in any status, or no lead for a user who may read no
     * lead.
     *
     * @param userId              the signed-in staff member
     * @param expandedAuthorities the user's authorities, already expanded through the role hierarchy
     */
    LeadScopeFacts scopeOf(String userId, Set<String> expandedAuthorities);
}
