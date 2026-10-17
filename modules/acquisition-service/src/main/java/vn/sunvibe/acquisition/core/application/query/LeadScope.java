package vn.sunvibe.acquisition.core.application.query;

/**
 * Which part of the lead queue a list shows (acquisition-owner-lead-queue spec 6). A SALE only ever sees the leads
 * assigned to them, whatever the scope.
 */
public enum LeadScope {
    /** Leads assigned to the caller; the default. */
    MINE,
    /** Leads nobody has taken yet: status NEW. */
    UNASSIGNED,
    /** Every lead. */
    ALL
}
