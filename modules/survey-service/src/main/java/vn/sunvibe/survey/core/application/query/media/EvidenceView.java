package vn.sunvibe.survey.core.application.query.media;

import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;

/**
 * A frozen photo with the reviewer's mark (survey-checklist submission spec 7.2, A-5): {@code acceptedOrNull} is null
 * before a decision; after it every photo the reviewer did not mark counts as accepted.
 */
public record EvidenceView(EvidenceEntry entry, Boolean acceptedOrNull, String rejectionReasonOrNull) {

    @Override
    public String toString() {
        return "EvidenceView[" + entry.mediaId() + "]";
    }
}
