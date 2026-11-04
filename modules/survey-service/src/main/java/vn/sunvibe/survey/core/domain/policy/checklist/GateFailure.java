package vn.sunvibe.survey.core.domain.policy.checklist;

/** One failing gate of a submission, named by its {@code errors[].field} (survey-checklist submission spec 7.3). */
public record GateFailure(String field) {
}
