package vn.sunvibe.survey.core.domain.policy.checklist;

/**
 * A unit a correction covered, as the save log keeps it (survey-checklist spec 5.2): its key and the code of its space
 * in the stored G1, so a removed space still shows its code; null for asset-level units.
 */
public record CorrectedUnit(String unitKey, String spaceCode) {
}
