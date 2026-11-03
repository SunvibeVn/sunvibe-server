package vn.sunvibe.survey.core.domain.policy.media;

import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

import java.util.Optional;

/**
 * Where a photo of a category may be placed against the current G1 (survey-media spec 4.6, A-9, A-12; create (10)):
 * a space of the current G1, the category's level, and SHARED_* only while the rental scope is PARTS.
 */
public final class PlacementRules {

    private PlacementRules() {
    }

    /** The request field to refuse ({@code spaceId} or {@code category}), or empty when the placement is allowed. */
    public static Optional<String> violation(EvidenceCategory category, String spaceIdOrNull,
                                             IdentificationContent g1) {
        if (spaceIdOrNull != null && findSpace(g1, spaceIdOrNull) == null) {
            return Optional.of("spaceId");
        }
        if (spaceIdOrNull == null ? !category.allowsAsset() : !category.allowsSpace()) {
            return Optional.of("category");
        }
        if (category.partsOnly() && g1.rentalScope() != RentalScope.PARTS) {
            return Optional.of("category");
        }
        return Optional.empty();
    }

    /** The observed space of G1 with this id, or null. */
    public static ObservedSpace findSpace(IdentificationContent g1, String spaceId) {
        if (g1.spaces() == null) {
            return null;
        }
        for (ObservedSpace space : g1.spaces()) {
            if (space.spaceId().equals(spaceId)) {
                return space;
            }
        }
        return null;
    }
}
