package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * A viewed rentable space (survey-checklist US-1 AC-3, BR-3): a client ULID that never changes, a code unique in the
 * checklist ignoring case, where it is and an optional description. A temporary identity, never a unit.
 */
public record ObservedSpace(String spaceId, String code, String location, String description) {

    @Override
    public String toString() {
        return "ObservedSpace[" + spaceId + "]";
    }
}
