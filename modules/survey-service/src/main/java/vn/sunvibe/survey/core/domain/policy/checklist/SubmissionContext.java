package vn.sunvibe.survey.core.domain.policy.checklist;

/**
 * What the submission gates read besides the record (survey-checklist submission spec 4.4): the two switches and a
 * SCHEDULED appointment on the lead.
 */
public record SubmissionContext(boolean uploadsOn, boolean submissionOn, boolean scheduledAppointment) {

    /** Nothing open: the read of a list, which shows no availability. */
    public static final SubmissionContext CLOSED = new SubmissionContext(false, false, false);
}
