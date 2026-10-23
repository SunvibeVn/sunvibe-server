package vn.sunvibe.survey.core.application.shared.external.lead;

/** How much of the lead queue a survey caller sees (acquisition-owner-lead-queue BR-1, survey-schedule BR-1). */
public enum ViewerAccess {
    /** Every lead: SALE_LEADER, SALES_MANAGER, ADMIN and SUPER_ADMIN. */
    ALL,
    /** Only the leads assigned to the caller: SALE. */
    OWN
}
