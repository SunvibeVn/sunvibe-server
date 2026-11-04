package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.testsupport.MediaFixture;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whether a PASSED revision can still be used (survey-checklist submission spec 4.7, D3-2, S-6, S-16). */
class PassedUsabilityTest {

    private static Usability of(int revisionCycle, int leadCycle, boolean leadClosed, List<SurveyMedia> frozen) {
        return PassedUsability.of(revisionCycle, leadCycle, leadClosed, frozen);
    }

    @Test
    void usability_everyFrozenPhotoFine_usable() {
        Usability usability = of(1, 1, false, List.of(MediaFixture.ready()));

        assertTrue(usability.usable());
        assertEquals(null, usability.reasonOrNull());
    }

    @Test
    void usability_intentChanged() {
        assertEquals(new Usability(false, UsabilityReason.INTENT_CHANGED), of(1, 2, false, List.of()));
    }

    @Test
    void usability_leadClosed() {
        assertEquals(new Usability(false, UsabilityReason.LEAD_CLOSED), of(1, 1, true, List.of()));
    }

    @Test
    void usability_heldEvidence_thenReleased() {
        SurveyMedia photo = MediaFixture.held(MediaFixture.ready(), HoldReason.SECURITY_INCIDENT);

        assertEquals(new Usability(false, UsabilityReason.EVIDENCE_HELD), of(1, 1, false, List.of(photo)));

        photo.release(MediaFixture.ADMIN, MediaFixture.T0.plusSeconds(600));

        assertTrue(of(1, 1, false, List.of(photo)).usable());
    }

    @Test
    void usability_contentDeleted_GONE() {
        SurveyMedia photo = MediaFixture.ready();
        photo.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, MediaFixture.T0.plusSeconds(300));
        photo.markContentDeleted(MediaFixture.T0.plusSeconds(600));

        assertEquals(new Usability(false, UsabilityReason.EVIDENCE_GONE), of(1, 1, false, List.of(photo)));
    }

    @Test
    void usability_contentMissing_GONE() {
        SurveyMedia photo = MediaFixture.ready();
        photo.markContentMissing(MediaFixture.T0.plusSeconds(600));

        assertEquals(new Usability(false, UsabilityReason.EVIDENCE_GONE), of(1, 1, false, List.of(photo)));
    }

    /** A release cannot bring back lost content, so the permanent reason is the one to show. */
    @Test
    void usability_heldAndGone_namesThePermanentReason() {
        SurveyMedia held = MediaFixture.held(MediaFixture.ready(), HoldReason.SECURITY_INCIDENT);
        SurveyMedia gone = MediaFixture.ready();
        gone.markContentMissing(MediaFixture.T0.plusSeconds(600));

        assertEquals(UsabilityReason.EVIDENCE_GONE, of(1, 1, false, List.of(held, gone)).reasonOrNull());
    }

    @Test
    void usability_closedLeadOfAnEndedCycle_namesTheCycleFirst() {
        Usability usability = of(1, 2, true, List.of());

        assertFalse(usability.usable());
        assertEquals(UsabilityReason.INTENT_CHANGED, usability.reasonOrNull());
    }
}
