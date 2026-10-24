package vn.sunvibe.survey.core.domain.model.checklist;

/** A link to a hazard of an earlier checklist of the same lead and intent cycle (survey-checklist BR-24). */
public record HazardLink(String checklistId, String hazardId) {
}
