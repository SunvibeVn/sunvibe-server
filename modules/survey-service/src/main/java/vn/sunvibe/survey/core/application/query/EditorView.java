package vn.sunvibe.survey.core.application.query;

import java.time.Instant;

/** Someone who saved the draft, with their latest save (survey-checklist spec 7.2 {@code editors[]}). */
public record EditorView(String userId, Instant lastSavedAt) {
}
