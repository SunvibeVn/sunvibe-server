package vn.sunvibe.acquisition.core.application.usecase.survey;

/** One broken meeting-place rule: the field ({@code provinceCode}, {@code wardCode} or {@code latitude}) and why. */
public record MeetingPlaceViolation(String field, String message) {
}
