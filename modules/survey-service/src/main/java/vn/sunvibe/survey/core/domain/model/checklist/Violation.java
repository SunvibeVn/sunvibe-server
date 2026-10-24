package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * One broken template rule: the JSON path of the request (dots and numeric indexes, {@code content.spaces.1.code})
 * and an English message that never quotes the value (survey-checklist spec 5.1, 7.3).
 */
public record Violation(String path, String message) {
}
