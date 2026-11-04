package vn.sunvibe.survey.core.application.shared;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.PlacedMedia;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.policy.media.MediaRemovalPolicy;
import vn.sunvibe.survey.core.domain.policy.media.PlacementRules;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSelectionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSetDomainRepository;

/**
 * What the checklist commands do to photos (survey-media spec A-9, 4.8, A-22, 5.4): a G1 save that ends placements
 * removes the selected photos on them and deselects the carried ones (checklist submission spec 4.6), and a takeover
 * ends the previous holder's open uploads. Both take the set row
 * and then the photo rows {@code FOR UPDATE}, after the checklist's own rows; photo ids come from a scalar query, so
 * no photo row is loaded before its lock.
 */
@Component
public class ChecklistMediaCascade {

    private final SurveyMediaSetDomainRepository sets;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaJobDomainRepository jobs;
    private final SurveyMediaSelectionDomainRepository selections;

    public ChecklistMediaCascade(SurveyMediaSetDomainRepository sets, SurveyMediaDomainRepository media,
                                 SurveyMediaJobDomainRepository jobs,
                                 SurveyMediaSelectionDomainRepository selections) {
        this.sets = sets;
        this.media = media;
        this.jobs = jobs;
        this.selections = selections;
    }

    /** A carried photo on a placement a G1 save ends: its selection and the photo as read. */
    public record EndedCarried(MediaSelection selection, SurveyMedia photo) {
    }

    /**
     * The selected photos on the placements a G1 save ends: the own ones under the set and photo locks, and the carried
     * ones, which the save only deselects, so their rows are read without a lock (submission spec 4.6).
     */
    public record Ended(MediaSet setOrNull, List<SurveyMedia> photos, List<EndedCarried> carried) {

        static final Ended NONE = new Ended(null, List.of(), List.of());

        /** spec A-9, BR-12: the received photos of other uploaders, which need the save's correction. */
        public List<SurveyMedia> needingCorrection(String callerUserId) {
            List<SurveyMedia> needing = new ArrayList<>(photos);
            carried.forEach(ended -> needing.add(ended.photo()));
            return needing.stream().filter(photo -> MediaRemovalPolicy.correctionRequired(photo, callerUserId))
                    .toList();
        }
    }

    /**
     * spec A-9: a save that removes a space, or changes the scope from PARTS to another value, ends placements; when
     * the checklist has a set row, the set and the selected photos on those placements are locked.
     */
    public Ended lockEnded(String checklistId, IdentificationContent stored, IdentificationContent next) {
        if (!endsAPlacement(stored, next)) {
            return Ended.NONE;
        }
        Optional<MediaSet> set = sets.lockForUpdate(checklistId);
        if (set.isEmpty()) {
            return Ended.NONE;
        }
        List<SurveyMediaId> ids = media.findSelectedPlacements(checklistId).stream()
                .filter(placed -> ended(placed, next)).map(PlacedMedia::id).toList();
        List<SurveyMedia> photos = ids.isEmpty() ? List.of() : media.lockForUpdate(ids).stream()
                .filter(photo -> photo.isSelected() && ended(new PlacedMedia(photo.getId(),
                        photo.getPlacement().spaceIdOrNull(), photo.getCategory()), next))
                .toList();
        return new Ended(set.get(), photos, endedCarried(checklistId, next));
    }

    private List<EndedCarried> endedCarried(String checklistId, IdentificationContent next) {
        Map<SurveyMediaId, MediaSelection> selected = new LinkedHashMap<>();
        selections.findByChecklistId(checklistId).stream().filter(MediaSelection::isSelected)
                .forEach(selection -> selected.put(selection.mediaId(), selection));
        if (selected.isEmpty()) {
            return List.of();
        }
        return media.findByIds(selected.keySet()).stream()
                .filter(photo -> ended(new PlacedMedia(photo.getId(), photo.getPlacement().spaceIdOrNull(),
                        photo.getCategory()), next))
                .map(photo -> new EndedCarried(selected.get(photo.getId()), photo)).toList();
    }

    /**
     * spec A-9, 4.7, A-23: each photo is removed as a cascade of the save by the caller, a READY one that is not held
     * with its deletion request and job; no History row of its own. The set version is bumped once.
     */
    public void remove(Ended ended, String callerUserId, long saveId, Instant now) {
        if (ended.photos().isEmpty() && ended.carried().isEmpty()) {
            return;
        }
        for (SurveyMedia photo : ended.photos()) {
            photo.remove(RemovalKind.CASCADE, callerUserId, null, saveId, now);
            media.update(photo);
            if (photo.getContentDeleteRequestedAt() != null) {
                jobs.upsertDeleteContent(photo.getId(), now.plus(MediaRemovalPolicy.CONTENT_DELETION_DELAY), now);
            }
        }
        // submission spec 4.6, A-12: a carried photo leaves this revision and stays as it is in its origin
        for (EndedCarried carried : ended.carried()) {
            selections.update(carried.selection().deselected(callerUserId, RemovalKind.CASCADE, null, saveId, now));
        }
        MediaSet set = ended.setOrNull();
        set.bump(now);
        sets.update(set);
    }

    /**
     * spec 4.8, A-22: after the takeover's checklist update, the previous holder's AWAITING_CONTENT and RECEIVING
     * photos become UPLOAD_EXPIRED, so an in-flight commit is refused and the new holder can upload the same files.
     */
    public void endOpenUploads(String checklistId, String previousHolderUserId, Instant now) {
        Optional<MediaSet> set = sets.lockForUpdate(checklistId);
        if (set.isEmpty()) {
            return;
        }
        List<SurveyMediaId> ids = media.findOpenUploadIds(checklistId, previousHolderUserId);
        if (ids.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (SurveyMedia photo : media.lockForUpdate(ids)) {
            if (photo.getStatus() == MediaStatus.AWAITING_CONTENT || photo.getStatus() == MediaStatus.RECEIVING) {
                photo.expire(now);
                media.update(photo);
                changed = true;
            }
        }
        if (changed) {
            set.get().bump(now);
            sets.update(set.get());
        }
    }

    private static boolean endsAPlacement(IdentificationContent stored, IdentificationContent next) {
        if (stored.rentalScope() == RentalScope.PARTS && next.rentalScope() != RentalScope.PARTS) {
            return true;
        }
        Set<String> kept = spaceIds(next);
        return spaceIds(stored).stream().anyMatch(spaceId -> !kept.contains(spaceId));
    }

    private static boolean ended(PlacedMedia placed, IdentificationContent next) {
        return PlacementRules.violation(placed.category(), placed.spaceIdOrNull(), next).isPresent();
    }

    private static Set<String> spaceIds(IdentificationContent g1) {
        return g1.spaces() == null ? Set.of() : g1.spaces().stream().map(ObservedSpace::spaceId)
                .collect(Collectors.toSet());
    }
}
