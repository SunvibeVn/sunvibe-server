package vn.sunvibe.acquisition.core.application.usecase.survey;

/**
 * How much of the lead queue a survey caller sees, from the lead queue's one role mapping
 * (acquisition-owner-lead-queue BR-1, survey-schedule BR-1).
 */
public enum SurveyLeadAccess {
    /** Every lead: SALE_LEADER, SALES_MANAGER, ADMIN and SUPER_ADMIN. */
    ALL,
    /** Only the leads assigned to the caller: SALE. */
    OWN
}
