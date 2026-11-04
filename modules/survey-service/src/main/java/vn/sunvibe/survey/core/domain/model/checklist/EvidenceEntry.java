package vn.sunvibe.survey.core.domain.model.checklist;

import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

/**
 * One photo of a submission's frozen set (survey-checklist submission spec 4.3): which photo, its category and
 * placement and the hash of its FULL variant.
 */
public record EvidenceEntry(String mediaId, EvidenceCategory category, String spaceIdOrNull, String spaceCodeOrNull,
                            String fullSha256) {

    public EvidenceEntry {
        if (mediaId == null || category == null || fullSha256 == null) {
            throw new IllegalArgumentException("An evidence entry needs its photo, category and FULL hash.");
        }
        if ((spaceIdOrNull == null) != (spaceCodeOrNull == null)) {
            throw new IllegalArgumentException("A space has both its id and its code.");
        }
    }
}
