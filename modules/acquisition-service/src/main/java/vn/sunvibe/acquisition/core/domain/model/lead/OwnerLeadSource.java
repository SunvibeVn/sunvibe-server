package vn.sunvibe.acquisition.core.domain.model.lead;

/**
 * How the lead reached Sunvibe.
 */
public enum OwnerLeadSource {
    /** The owner filled in the public web form. */
    WEB,
    /** Another owner or user recommended the owner. */
    REFERRAL,
    /** Staff found the owner through their own outreach. */
    OUTBOUND,
    /** A partner company handed the lead over. */
    PARTNER,
    /** Staff typed the lead in on behalf of the owner. */
    STAFF_ENTRY
}
