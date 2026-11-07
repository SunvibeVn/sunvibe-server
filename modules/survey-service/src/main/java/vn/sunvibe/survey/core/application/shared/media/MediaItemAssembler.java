package vn.sunvibe.survey.core.application.shared.media;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.query.media.MediaItemView;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.domain.model.media.MediaHold;
import vn.sunvibe.survey.core.domain.model.media.MediaRemoval;
import vn.sunvibe.survey.core.domain.model.media.MediaUnavailableReason;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.policy.media.MediaAvailability;

/**
 * Photos as a viewer sees them (survey-media spec 7.2 {@code SurveyMediaItem}), with the names of uploaders, removers
 * and, for a viewer who may hold photos, holders in one directory lookup. The caption is hidden while held and after a
 * CREDENTIAL_EXPOSURE release from a viewer without hold rights (A-23); a released hold is not shown.
 */
@Component
public class MediaItemAssembler {

    private final SurveyorDirectoryPort directory;

    public MediaItemAssembler(SurveyorDirectoryPort directory) {
        this.directory = directory;
    }

    public MediaItemView assemble(SurveyMedia media, boolean viewerCanHold) {
        return assemble(List.of(media), viewerCanHold).get(0);
    }

    public List<MediaItemView> assemble(List<SurveyMedia> photos, boolean viewerCanHold) {
        return assemble(photos, viewerCanHold, Map.of());
    }

    /**
     * @param carriedFrom the number of the revision each carried photo came from, by photo id (checklist submission
     *                    spec 7.2)
     */
    public List<MediaItemView> assemble(List<SurveyMedia> photos, boolean viewerCanHold,
                                        Map<String, Integer> carriedFrom) {
        Set<String> userIds = new HashSet<>();
        for (SurveyMedia media : photos) {
            userIds.add(media.getUploadedByUserId());
            if (media.getRemoval() != null) {
                userIds.add(media.getRemoval().byUserId());
            }
            if (viewerCanHold && media.isHeld()) {
                userIds.add(media.getHold().heldByUserId());
            }
        }
        Map<String, String> names = userIds.isEmpty() ? Map.of() : directory.displayNames(userIds);
        return photos.stream().map(media -> view(media, viewerCanHold, names,
                carriedFrom.get(media.getId().getValue()))).toList();
    }

    private static MediaItemView view(SurveyMedia media, boolean viewerCanHold, Map<String, String> names,
                                      Integer carriedFromChecklistNoOrNull) {
        boolean captionHidden = media.getHold().captionHidden(viewerCanHold);
        MediaVariant full = media.getVariants().stream().filter(variant -> variant.kind() == VariantKind.FULL)
                .max(Comparator.comparingInt(MediaVariant::attempt)).orElse(null);
        MediaRemoval removal = media.getRemoval();
        MediaItemView.Removed removed = removal == null ? null : new MediaItemView.Removed(removal.at(),
                removal.byUserId(), names.get(removal.byUserId()), removal.kind(), removal.correctionOrNull(),
                removal.bySaveIdOrNull());
        MediaHold hold = media.getHold();
        MediaItemView.Held held = null;
        if (media.isHeld()) {
            held = viewerCanHold
                    ? new MediaItemView.Held(hold.heldAt(), hold.heldByUserId(), names.get(hold.heldByUserId()),
                            hold.reason())
                    : new MediaItemView.Held(hold.heldAt(), null, null, null);
        }
        MediaUnavailableReason unavailable = MediaAvailability.unavailableReason(media, viewerCanHold);
        return new MediaItemView(media.getId().getValue(), media.getMediaNo(), media.getStatus(),
                media.getStatusReason(), media.getPlacement().spaceIdOrNull(),
                media.getPlacement().spaceCodeAtUploadOrNull(), media.getCategory(),
                captionHidden ? null : media.getCaption(), captionHidden, media.getUploadedByUserId(),
                names.get(media.getUploadedByUserId()), media.getCreatedAt(), media.getContentReceivedAt(),
                media.getReadyAt(), full == null ? null : full.width(), full == null ? null : full.height(), removed,
                held, unavailable == null, unavailable, carriedFromChecklistNoOrNull);
    }
}
