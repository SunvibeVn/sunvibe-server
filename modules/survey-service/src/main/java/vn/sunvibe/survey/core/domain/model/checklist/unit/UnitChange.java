package vn.sunvibe.survey.core.domain.model.checklist.unit;

/**
 * One changed unit of a save (survey-checklist spec A-19): its key and kind, the unit before (null when new) and
 * after (null when removed), the stored metadata (its tombstone for a key coming back) and the JSON path of the unit
 * in the request (null for units the request does not hold, such as those of a cascade).
 */
public record UnitChange(UnitKey key, ChangeKind kind, ProvenanceUnit before, ProvenanceUnit after,
                         UnitMeta storedMeta, String path) {
}
