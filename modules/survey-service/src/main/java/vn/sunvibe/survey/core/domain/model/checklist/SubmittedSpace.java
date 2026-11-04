package vn.sunvibe.survey.core.domain.model.checklist;

/** A space of G1 as a submission keeps it (survey-checklist submission spec 4.3, 4.7): its id and its code. */
public record SubmittedSpace(String spaceId, String code) {
}
