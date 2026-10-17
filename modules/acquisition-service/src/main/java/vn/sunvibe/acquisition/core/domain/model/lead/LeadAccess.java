package vn.sunvibe.acquisition.core.domain.model.lead;

/**
 * How much of the lead queue a staff member may see (acquisition-owner-lead-queue BR-1: a SALE sees only the leads
 * assigned to them, the other sale roles and ADMIN see every lead).
 */
public enum LeadAccess {
    /** Every lead: SALE_LEADER, SALES_MANAGER, ADMIN and SUPER_ADMIN. */
    ALL,
    /** Only the leads assigned to the viewer: SALE. */
    OWN
}
