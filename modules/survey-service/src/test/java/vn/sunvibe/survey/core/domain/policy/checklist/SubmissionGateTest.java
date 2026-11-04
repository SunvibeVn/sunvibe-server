package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.testsupport.MediaFixture;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The gates S2..S7 of a submission (survey-checklist submission spec 4.4, 7.3, S-2, D3-2, D3-7, D3-11). */
class SubmissionGateTest {

    private static final MissingItem NOT_CHECKED = new MissingItem(ChecklistGroup.PARTS, null, "accessRoute",
            MissingProblem.NOT_CHECKED);

    private static List<String> fields(GateFacts facts) {
        return SubmissionGate.check(facts).stream().map(GateFailure::field).toList();
    }

    private static GateFacts open(List<MissingItem> missing, List<SurveyMedia> photos) {
        return new GateFacts(true, true, false, missing, photos);
    }

    @Test
    void gate_everythingFine_passes() {
        assertEquals(List.of(), fields(open(List.of(), List.of(MediaFixture.ready()))));
    }

    @Test
    void gate_uploadsOff_fieldSubmission() {
        assertEquals(List.of("submission"), fields(new GateFacts(false, true, false, List.of(), List.of())));
    }

    @Test
    void gate_submissionOff_fieldSubmission() {
        assertEquals(List.of("submission"), fields(new GateFacts(true, false, false, List.of(), List.of())));
    }

    @Test
    void gate_scheduledAppointment_fieldAppointment() {
        assertEquals(List.of("appointment"), fields(new GateFacts(true, true, true, List.of(), List.of())));
    }

    @Test
    void gate_missingItem_fieldReadinessMissing() {
        assertEquals(List.of("readiness.missing"), fields(open(List.of(NOT_CHECKED), List.of())));
    }

    @Test
    void gate_pendingPhoto_mediaPending() {
        assertEquals(List.of("media.pending"), fields(open(List.of(), List.of(MediaFixture.processing()))));
        assertEquals(List.of("media.pending"), fields(open(List.of(), List.of(MediaFixture.awaiting()))));
        assertEquals(List.of("media.pending"), fields(open(List.of(), List.of(MediaFixture.receiving()))));
    }

    @Test
    void gate_101Selected_mediaLimit() {
        List<SurveyMedia> photos = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            photos.add(ready(i));
        }

        assertEquals(List.of("media.limit"), fields(open(List.of(), photos)));
    }

    @Test
    void gate_100Selected_passes() {
        List<SurveyMedia> photos = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            photos.add(ready(i));
        }

        assertEquals(List.of(), fields(open(List.of(), photos)));
    }

    @Test
    void gate_heldPhoto_mediaUnavailable() {
        SurveyMedia held = MediaFixture.held(MediaFixture.ready(), HoldReason.SECURITY_INCIDENT);

        assertEquals(List.of("media.unavailable"), fields(open(List.of(), List.of(held))));
    }

    @Test
    void gate_contentMissing_mediaUnavailable() {
        SurveyMedia photo = MediaFixture.ready();
        photo.markContentMissing(MediaFixture.T0.plusSeconds(600));

        assertEquals(List.of("media.unavailable"), fields(open(List.of(), List.of(photo))));
    }

    /** spec 4.4: the readiness item of a held photo is gate S7's, not S4's, so the first failing gate stays S7. */
    @Test
    void gate_onlyUnavailableItems_failsS7NotS4() {
        MissingItem unavailable = new MissingItem(ChecklistGroup.EVIDENCE, null, "EXTERIOR_ACCESS",
                MissingProblem.EVIDENCE_UNAVAILABLE);
        SurveyMedia held = MediaFixture.held(MediaFixture.ready(), HoldReason.SECURITY_INCIDENT);

        assertEquals(List.of("media.unavailable"), fields(open(List.of(unavailable), List.of(held))));
    }

    @Test
    void gate_removedOrFinishedPhotos_doNotCount() {
        SurveyMedia removed = MediaFixture.ready();
        removed.remove(vn.sunvibe.survey.core.domain.model.media.RemovalKind.HOLDER, MediaFixture.SALE, null, null,
                MediaFixture.T0.plusSeconds(300));

        assertEquals(List.of(), fields(open(List.of(), List.of(removed, MediaFixture.rejected(), MediaFixture.expired()))));
    }

    @Test
    void gate_everyFailure_inTheOrderOfSpec44() {
        SurveyMedia held = MediaFixture.held(MediaFixture.ready(), HoldReason.SECURITY_INCIDENT);
        List<SurveyMedia> photos = new ArrayList<>(List.of(held, MediaFixture.processing()));
        for (int i = 0; i < 100; i++) {
            photos.add(ready(i));
        }

        assertEquals(List.of("submission", "appointment", "readiness.missing", "media.pending", "media.limit",
                "media.unavailable"), fields(new GateFacts(false, false, true, List.of(NOT_CHECKED), photos)));
    }

    // ==================== the reason of the read (spec 4.4) ====================

    @Test
    void reason_allPass_none() {
        assertEquals(null, SubmissionGate.unavailableReason(open(List.of(), List.of()), true));
    }

    /** Read as before this slice (spec S-19): without photos the evidence reason comes first. */
    @Test
    void reason_photosNotAvailable_evidenceNotAvailable() {
        assertEquals("EVIDENCE_NOT_AVAILABLE", SubmissionGate.unavailableReason(
                new GateFacts(false, false, false, List.of(), List.of()), false));
    }

    @Test
    void reason_submissionOff_submissionNotAvailable() {
        assertEquals("SUBMISSION_NOT_AVAILABLE", SubmissionGate.unavailableReason(
                new GateFacts(true, false, false, List.of(), List.of()), true));
    }

    @Test
    void reason_uploadsOffWithPhotos_evidenceNotAvailable() {
        assertEquals("EVIDENCE_NOT_AVAILABLE", SubmissionGate.unavailableReason(
                new GateFacts(false, true, false, List.of(), List.of()), true));
    }

    @Test
    void reason_namesTheFirstFailingGate() {
        SurveyMedia held = MediaFixture.held(MediaFixture.ready(), HoldReason.SECURITY_INCIDENT);

        assertEquals("APPOINTMENT_SCHEDULED", SubmissionGate.unavailableReason(
                new GateFacts(true, true, true, List.of(NOT_CHECKED), List.of()), true));
        assertEquals("MISSING_ITEMS", SubmissionGate.unavailableReason(open(List.of(NOT_CHECKED),
                List.of(MediaFixture.processing())), true));
        assertEquals("PHOTOS_NOT_FINISHED", SubmissionGate.unavailableReason(open(List.of(),
                List.of(MediaFixture.processing())), true));
        assertEquals("PHOTO_UNAVAILABLE", SubmissionGate.unavailableReason(open(List.of(), List.of(held)), true));
    }

    @Test
    void reason_tooManyPhotos_photoLimit() {
        List<SurveyMedia> photos = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            photos.add(ready(i));
        }

        assertEquals("PHOTO_LIMIT", SubmissionGate.unavailableReason(open(List.of(), photos), true));
    }

    private static SurveyMedia ready(int n) {
        return MediaFixture.ready(MediaFixture.awaiting(EvidenceCategory.DETAIL, MediaPlacement.asset(),
                MediaFixture.SALE, MediaFixture.sha(1000 + n)));
    }
}
