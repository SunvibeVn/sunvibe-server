package vn.sunvibe.survey.core.presentation.dto.response.media;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.policy.media.CoverageState;

/**
 * The photos of a checklist for the viewer (survey-media spec 7.2): send
 * {@code setVersion.identificationVersion.conditionVersion.checklistVersion.stateEpoch.viewDigest} back as
 * {@code knownVersion} to get a 204 while nothing changed (A-30).
 */
@Schema(description = "The photos of a survey checklist")
public record ChecklistMediaResponse(
        String checklistId,
        long setVersion,
        Basis basis,
        @Schema(description = "Uploads on, intake not paused and the viewer could create a photo now")
        boolean uploadEnabled,
        boolean intakePaused,
        boolean servingEnabled,
        @Schema(description = "The viewer may hold photos (ADMIN)")
        boolean canHold,
        Limits limits,
        @Schema(description = "Every selected photo, by number")
        List<SurveyMediaItem> items,
        @Schema(description = "REJECTED, FAILED, CANCELLED and UPLOAD_EXPIRED photos that are not removed; newest 100")
        List<SurveyMediaItem> notAcceptedItems,
        @Schema(description = "Removed photos; newest 100")
        List<SurveyMediaItem> removedItems,
        Totals totals,
        @Schema(description = "Every allowed placement and category pair")
        List<Coverage> coverage) {

    /** The versions the view was computed from (spec A-30). */
    @Schema(name = "ChecklistMediaBasis")
    public record Basis(long identificationVersion, long conditionVersion, long checklistVersion, long stateEpoch,
                        @Schema(description = "8 hex characters over this viewer's editability and switches")
                        String viewDigest) {
    }

    /** spec 7.2 {@code limits}. */
    @Schema(name = "ChecklistMediaLimits")
    public record Limits(long maxFileBytes, int maxSelected, int selectedCount, int maxPending, int pendingCount) {
    }

    /** All not accepted and removed photos, beyond the newest 100 shown. */
    @Schema(name = "ChecklistMediaTotals")
    public record Totals(int notAccepted, int removed) {
    }

    /** spec 4.6: one placement and category pair. */
    @Schema(name = "ChecklistMediaCoverage")
    public record Coverage(
            @Schema(nullable = true, description = "Null for the whole asset")
            String spaceId,
            EvidenceCategory category,
            boolean required,
            @Schema(nullable = true, description = "The G4 reason that exempts a required category")
            String notRequiredReason,
            @Schema(description = "Usable photos of the pair")
            int readyCount,
            @Schema(description = "Selected, not held PROCESSING photos of the pair")
            int processingCount,
            CoverageState state) {
    }
}
