package vn.sunvibe.survey.core.domain.model.media;

import java.time.Instant;

/**
 * A photo carried into a later revision of its visit (survey-checklist submission spec 4.6, 5.3, A-12): the photo keeps
 * its rows and its origin; this row says the revision selects it. A deselection only marks this row, never the photo:
 * the earlier revision still lists and serves it. The holder's deselection keeps its correction here; a G1 cascade
 * names the save, whose row keeps the correction (survey-media spec A-9).
 */
public record MediaSelection(String checklistId, SurveyMediaId mediaId, String carriedFromChecklistId,
                             Instant selectedAt, Instant deselectedAtOrNull, String deselectedByUserIdOrNull,
                             RemovalKind deselectionKindOrNull, Long deselectedBySaveIdOrNull,
                             MediaRemovalCorrection correctionOrNull) {

    public MediaSelection {
        if (checklistId == null || mediaId == null || carriedFromChecklistId == null || selectedAt == null) {
            throw new IllegalArgumentException("A selection names its revision, photo, origin and time.");
        }
    }

    /** A photo carried into the revision. */
    public static MediaSelection carried(String checklistId, SurveyMediaId mediaId, String carriedFromChecklistId,
                                         Instant at) {
        return new MediaSelection(checklistId, mediaId, carriedFromChecklistId, at, null, null, null, null, null);
    }

    /** The revision still selects the photo. */
    public boolean isSelected() {
        return deselectedAtOrNull == null;
    }

    /** The same selection after a deselection by the holder (kind HOLDER) or a G1 save's cascade (kind CASCADE). */
    public MediaSelection deselected(String byUserId, RemovalKind kind, MediaRemovalCorrection correction, Long saveId,
                                     Instant at) {
        if (!isSelected()) {
            throw new IllegalStateException("The photo already left this revision.");
        }
        return new MediaSelection(checklistId, mediaId, carriedFromChecklistId, selectedAt, at, byUserId, kind, saveId,
                correction);
    }

    /** The correction is staff text: never print it. */
    @Override
    public String toString() {
        return "MediaSelection[" + checklistId + " " + mediaId + "]";
    }
}
