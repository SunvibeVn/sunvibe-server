package vn.sunvibe.acquisition.core.application.shared;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;

import java.util.Optional;
import java.util.Set;

/**
 * The one mapping from a staff member's roles to how much of the lead queue they see (acquisition-owner-lead-queue
 * BR-1): SALE_LEADER, SALES_MANAGER and ADMIN see every lead, a SALE only the leads assigned to them, and a person
 * with both SALE and a leader role sees every lead. The lead queue and survey-service both ask it (survey-schedule
 * spec 5.4), so the two never disagree. The roles must already be expanded through the role hierarchy (SUPER_ADMIN
 * reaches ADMIN there, not here).
 */
@Component
public class LeadAccessPolicy {

    private static final Set<String> ALL_LEADS_ROLES = Set.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN");
    private static final String OWN_LEADS_ROLE = "ROLE_SALE";

    /** The access of these expanded authorities, or empty when none of them may read owner leads. */
    public Optional<LeadAccess> accessFor(Set<String> expandedAuthorities) {
        if (expandedAuthorities.stream().anyMatch(ALL_LEADS_ROLES::contains)) {
            return Optional.of(LeadAccess.ALL);
        }
        if (expandedAuthorities.contains(OWN_LEADS_ROLE)) {
            return Optional.of(LeadAccess.OWN);
        }
        return Optional.empty();
    }
}
