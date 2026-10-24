package vn.sunvibe.survey.core.domain.model.checklist.unit;

import java.time.Instant;

/** A group row as read: its document, version and last save (null until the first save). */
public record GroupState(GroupDocument document, long version, Instant updatedAt, String updatedByUserId) {
}
