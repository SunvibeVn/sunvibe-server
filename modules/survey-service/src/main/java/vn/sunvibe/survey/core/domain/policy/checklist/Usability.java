package vn.sunvibe.survey.core.domain.policy.checklist;

/** Whether a PASSED revision can still be used, and why not (survey-checklist submission spec 4.7). */
public record Usability(boolean usable, UsabilityReason reasonOrNull) {

    public static final Usability USABLE = new Usability(true, null);

    public static Usability not(UsabilityReason reason) {
        return new Usability(false, reason);
    }
}
