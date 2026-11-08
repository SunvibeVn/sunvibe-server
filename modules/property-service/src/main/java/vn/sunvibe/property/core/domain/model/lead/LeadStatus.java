package vn.sunvibe.property.core.domain.model.lead;

/**
 * The status of an owner lead as acquisition reports it through the lead port (clean-up spec section 7). The names are
 * acquisition's; LeadStatusContractTest in app keeps the two enums equal.
 */
public enum LeadStatus {
    NEW,
    VALIDATING,
    ASSIGNED,
    CONTACTED,
    SURVEY_SCHEDULED,
    QUALIFIED,
    AGREEMENT_PENDING,
    ONBOARDED,
    ON_HOLD,
    DUPLICATE,
    REJECTED,
    WITHDRAWN;

    /** A closed lead takes no new work: a duplicate, a rejection or a withdrawal. */
    public boolean isClosed() {
        return this == DUPLICATE || this == REJECTED || this == WITHDRAWN;
    }
}
