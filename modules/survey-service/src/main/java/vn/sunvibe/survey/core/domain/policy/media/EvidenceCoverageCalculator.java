package vn.sunvibe.survey.core.domain.policy.media;

import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Evidence coverage of a checklist, computed on every read from G1, G4 and the photos (survey-media spec 4.6, BR-14).
 * The allowed pairs: the asset with EXTERIOR_ACCESS, the SHARED_* categories while the scope is PARTS, and DETAIL;
 * each observed space of G1 (when a scope is set) with MAIN_SPACE, KITCHEN, BATHROOM and DETAIL. A required pair is
 * COVERED with a usable photo of exactly that placement and category, otherwise PROCESSING with a selected PROCESSING
 * photo that is not held, otherwise MISSING. Only G4 NOT_APPLICABLE exempts a category (unknown keeps it required).
 * Without a scope or without spaces only EXTERIOR_ACCESS is evaluated. Photos never change survey data.
 */
public final class EvidenceCoverageCalculator {

    private EvidenceCoverageCalculator() {
    }

    /**
     * @param g4OrNull G4 as read with G1 (null: nothing recorded, every category stays required)
     */
    public static EvidenceCoverage of(IdentificationContent g1, ConditionContent g4OrNull,
                                      Collection<SurveyMedia> photos) {
        List<ObservedSpace> spaces = g1.rentalScope() == null || g1.spaces() == null ? List.of() : g1.spaces();
        boolean evaluated = !spaces.isEmpty();
        boolean parts = g1.rentalScope() == RentalScope.PARTS;
        List<CoveragePair> pairs = new ArrayList<>();

        pairs.add(required(null, EvidenceCategory.EXTERIOR_ACCESS, photos));
        if (parts) {
            Field<AreaCheck> sharedKitchen = g4OrNull == null ? null : g4OrNull.sharedKitchen();
            Field<AreaCheck> sharedBathroom = g4OrNull == null ? null : g4OrNull.sharedBathroom();
            pairs.add(sharedPair(EvidenceCategory.SHARED_KITCHEN, sharedKitchen, evaluated, photos));
            pairs.add(sharedPair(EvidenceCategory.SHARED_BATHROOM, sharedBathroom, evaluated, photos));
            pairs.add(optional(null, EvidenceCategory.SHARED_AMENITY, photos));
        }
        pairs.add(optional(null, EvidenceCategory.DETAIL, photos));
        for (ObservedSpace space : spaces) {
            ConditionSpace condition = conditionOf(g4OrNull, space.spaceId());
            pairs.add(required(space.spaceId(), EvidenceCategory.MAIN_SPACE, photos));
            pairs.add(exemptable(space.spaceId(), EvidenceCategory.KITCHEN,
                    condition == null ? null : condition.kitchen(), photos));
            pairs.add(exemptable(space.spaceId(), EvidenceCategory.BATHROOM,
                    condition == null ? null : condition.bathroom(), photos));
            pairs.add(optional(space.spaceId(), EvidenceCategory.DETAIL, photos));
        }
        return new EvidenceCoverage(pairs);
    }

    private static CoveragePair sharedPair(EvidenceCategory category, Field<AreaCheck> g4Field, boolean evaluated,
                                           Collection<SurveyMedia> photos) {
        if (!evaluated) {
            // survey-media spec 4.6: without spaces only EXTERIOR_ACCESS is evaluated (plan ruling: listed, not required)
            return notRequired(null, category, naReason(g4Field), photos);
        }
        return exemptable(null, category, g4Field, photos);
    }

    private static CoveragePair exemptable(String spaceId, EvidenceCategory category, Field<AreaCheck> g4Field,
                                           Collection<SurveyMedia> photos) {
        if (g4Field != null && g4Field.state() == FieldState.NOT_APPLICABLE) {
            return notRequired(spaceId, category, g4Field.naReason(), photos);
        }
        return required(spaceId, category, photos);
    }

    private static CoveragePair required(String spaceId, EvidenceCategory category, Collection<SurveyMedia> photos) {
        int ready = readyCount(spaceId, category, photos);
        int processing = processingCount(spaceId, category, photos);
        CoverageState state = ready > 0 ? CoverageState.COVERED
                : processing > 0 ? CoverageState.PROCESSING : CoverageState.MISSING;
        return new CoveragePair(spaceId, category, true, null, ready, processing, state);
    }

    private static CoveragePair notRequired(String spaceId, EvidenceCategory category, String reasonOrNull,
                                            Collection<SurveyMedia> photos) {
        return new CoveragePair(spaceId, category, false, reasonOrNull, readyCount(spaceId, category, photos),
                processingCount(spaceId, category, photos), CoverageState.NOT_REQUIRED);
    }

    private static CoveragePair optional(String spaceId, EvidenceCategory category, Collection<SurveyMedia> photos) {
        return new CoveragePair(spaceId, category, false, null, readyCount(spaceId, category, photos),
                processingCount(spaceId, category, photos), CoverageState.OPTIONAL);
    }

    /** survey-media spec 4.2: usable = servable; it counts for its own placement and category only. */
    private static int readyCount(String spaceId, EvidenceCategory category, Collection<SurveyMedia> photos) {
        return (int) photos.stream().filter(p -> on(p, spaceId, category) && p.isServable()).count();
    }

    /** survey-media spec 4.6, A-23: selected PROCESSING photos that are not held; uploads still streaming do not count. */
    private static int processingCount(String spaceId, EvidenceCategory category, Collection<SurveyMedia> photos) {
        return (int) photos.stream().filter(p -> on(p, spaceId, category) && p.isSelected()
                && p.getStatus() == MediaStatus.PROCESSING && !p.isHeld()).count();
    }

    private static boolean on(SurveyMedia photo, String spaceId, EvidenceCategory category) {
        return photo.getCategory() == category && Objects.equals(photo.getPlacement().spaceIdOrNull(), spaceId);
    }

    private static String naReason(Field<AreaCheck> g4Field) {
        return g4Field != null && g4Field.state() == FieldState.NOT_APPLICABLE ? g4Field.naReason() : null;
    }

    private static ConditionSpace conditionOf(ConditionContent g4OrNull, String spaceId) {
        if (g4OrNull == null || g4OrNull.spaces() == null) {
            return null;
        }
        for (ConditionSpace space : g4OrNull.spaces()) {
            if (space.spaceId().equals(spaceId)) {
                return space;
            }
        }
        return null;
    }
}
