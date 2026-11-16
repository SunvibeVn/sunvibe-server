package vn.sunvibe.survey.core.application.query.media;

import vn.sunvibe.survey.core.application.query.common.RefusedPhotoView;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;

import java.util.List;

/**
 * A frozen photo with the reviewer's mark (survey-checklist submission spec 7.2, A-5): {@code acceptedOrNull} is null
 * before a decision; after it every photo the reviewer did not mark counts as accepted. {@code refusedSameContent}
 * lists the photos that a decision on an earlier revision of the visit did not accept and whose source content is the
 * same, newest revision first (survey-checklist SV-310); it informs the reviewer and blocks nothing.
 */
public record EvidenceView(EvidenceEntry entry, Boolean acceptedOrNull, String rejectionReasonOrNull,
                           List<RefusedPhotoView> refusedSameContent) {

    public EvidenceView {
        refusedSameContent = List.copyOf(refusedSameContent);
    }

    @Override
    public String toString() {
        return "EvidenceView[" + entry.mediaId() + "]";
    }
}
