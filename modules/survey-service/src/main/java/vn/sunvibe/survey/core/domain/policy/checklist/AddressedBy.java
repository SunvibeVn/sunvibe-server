package vn.sunvibe.survey.core.domain.policy.checklist;

/** A later hazard that addresses an earlier one (survey-checklist spec A-20, 7.2 {@code addressedBy}). */
public record AddressedBy(String checklistId, int checklistNo, String hazardId, AddressKind kind) {
}
