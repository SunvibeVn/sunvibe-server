package vn.sunvibe.survey.core.application.shared.external.lead;

/** One broken meeting-place rule: {@code provinceCode}, {@code wardCode} or {@code latitude}, and why. */
public record PlaceViolation(String field, String message) {
}
