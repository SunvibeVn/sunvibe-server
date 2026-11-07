package vn.sunvibe.survey.core.application.query.media;

import java.util.List;

import vn.sunvibe.survey.core.domain.policy.media.CoveragePair;
import vn.sunvibe.survey.core.domain.policy.media.KnownVersion;

/**
 * The photos of a checklist for one viewer (survey-media spec 7.2 {@code ChecklistMediaResponse}), read in one
 * repeatable-read snapshot: the version the view was computed from ({@code basis}, whose {@code format()} is the
 * {@code knownVersion} a client sends back), the switches as they apply to this viewer, the limits, the selected photos
 * by number, the newest 100 not accepted and removed ones with their totals, and the coverage of every allowed pair.
 */
public record MediaListView(String checklistId, KnownVersion basis, boolean uploadEnabled, boolean intakePaused,
                            boolean servingEnabled, boolean canHold, Limits limits, List<MediaItemView> items,
                            List<MediaItemView> notAcceptedItems, List<MediaItemView> removedItems,
                            int totalNotAccepted, int totalRemoved, List<CoveragePair> coverage) {

    /** spec 7.2 {@code limits}. */
    public record Limits(long maxFileBytes, int maxSelected, int selectedCount, int maxPending, int pendingCount) {
    }

    /** Captions are inside the items: ids only. */
    @Override
    public String toString() {
        return "MediaListView[" + checklistId + " " + basis.format() + "]";
    }
}
