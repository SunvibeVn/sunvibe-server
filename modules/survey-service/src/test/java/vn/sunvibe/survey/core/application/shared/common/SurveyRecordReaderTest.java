package vn.sunvibe.survey.core.application.shared.common;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.query.appointment.BookingBlock;
import vn.sunvibe.survey.core.application.query.appointment.BookingState;
import vn.sunvibe.survey.core.application.shared.appointment.SurveyBlockAssembler;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader.CycleRecord;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.checklist.ResumeTarget;
import vn.sunvibe.survey.core.domain.policy.checklist.SurveyState;
import vn.sunvibe.survey.core.domain.policy.checklist.UsabilityReason;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;
import vn.sunvibe.survey.core.testsupport.MediaFixture;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;

/**
 * What the record answers once a PASSED revision is marked (property-create requalification spec 4, D5-1, D5-3, S-3,
 * S-7): every guard reads the one unmarked PASSED of the cycle, so booking and resume behave as before a PASSED.
 */
class SurveyRecordReaderTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();

    private SurveyChecklistId passed() {
        SurveyChecklistId id = h.started(LEAD, SALE_A).view().checklist().getId();
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.PASSED, null, null, LEADER);
        return id;
    }

    private void mark(SurveyChecklistId id) {
        SurveyChecklist checklist = h.checklists.findById(id).orElseThrow();
        checklist.markRequalified(Instant.now());
        h.checklists.update(checklist);
    }

    private CycleRecord record() {
        return h.records.read(LEAD, 1, h.appointments.findByLeadId(LEAD));
    }

    private BookingState booking(String status) {
        h.lead(LEAD, status, SALE_A);
        return vn.sunvibe.survey.core.application.shared.appointment.SurveyBlockAssembler.bookingState(h.leads.get(LEAD),
                h.appointments.findByLeadId(LEAD), true, record());
    }

    private SurveyMedia frozenPhoto(SurveyChecklistId id) {
        SurveyMedia photo = h.readyPhoto(id, EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset());
        h.evidence.saveAll(id, List.of(new EvidenceEntry(photo.getId().getValue(), photo.getCategory(), null, null,
                MediaFixture.sha(1))));
        return photo;
    }

    @Test
    void passed_afterMark_empty() {
        SurveyChecklistId id = passed();
        assertEquals(Optional.of(id), record().passed().map(SurveyChecklist::getId));

        mark(id);

        assertEquals(Optional.empty(), record().passed());
        assertFalse(record().hasPassed());
    }

    @Test
    void booking_afterMark_allowed() {
        SurveyChecklistId id = passed();
        assertEquals(BookingBlock.CHECKLIST_PASSED, booking("CONTACTED").blockedBy());

        mark(id);

        assertTrue(booking("CONTACTED").allowed());
    }

    @Test
    void resume_afterMark_notQualified() {
        SurveyChecklistId id = passed();
        assertEquals(Optional.of(ResumeTarget.QUALIFIED), h.records.resumeTarget(record(), 1));

        mark(id);

        assertEquals(Optional.empty(), h.records.resumeTarget(record(), 1));
    }

    /** D5-3: a release cannot bring a file back, so the mark is the way out of EVIDENCE_GONE (W18 9.7). */
    @Test
    void gone_thenMark_cycleFree() {
        SurveyChecklistId id = h.started(LEAD, SALE_A).view().checklist().getId();
        SurveyMedia photo = frozenPhoto(id);
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.PASSED, null, null, LEADER);
        photo.markContentMissing(Instant.now());
        h.media.put(photo);
        assertEquals(new SurveyState(true, false, false, false), h.records.state(record(), 1, false));
        assertEquals(BookingBlock.CHECKLIST_PASSED, booking("CONTACTED").blockedBy());

        mark(id);

        assertEquals(new SurveyState(false, false, false, false), h.records.state(record(), 1, false));
        assertTrue(booking("CONTACTED").allowed());
    }

    /** D5-3: a hold is reversible, so it suspends with no command and the release restores (S-7). */
    @Test
    void held_thenReleased_noMarkNeeded() {
        SurveyChecklistId id = h.started(LEAD, SALE_A).view().checklist().getId();
        SurveyMedia photo = frozenPhoto(id);
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.PASSED, null, null, LEADER);
        photo.hold(MediaFixture.ADMIN, HoldReason.SECURITY_INCIDENT, Instant.now());
        h.media.put(photo);
        assertEquals(new SurveyState(true, false, false, false), h.records.state(record(), 1, false));

        photo.release(MediaFixture.ADMIN, Instant.now());
        h.media.put(photo);

        assertEquals(new SurveyState(true, true, false, false), h.records.state(record(), 1, false));
    }

    @Test
    void usability_ofTheMarkedRevision_readsRequalified() {
        SurveyChecklistId id = passed();
        mark(id);

        SurveyChecklist marked = h.checklists.findById(id).orElseThrow();

        assertEquals(UsabilityReason.REQUALIFIED, h.records.usability(marked, 2, false).reasonOrNull());
    }

    @Test
    void hazards_ofAMarkedRevision_areFlagged() {
        SurveyChecklistId id = passed();
        assertFalse(ChecklistViewAssembler.hazards(h.checklists.findById(id).orElseThrow(), h.groups.findAll(id))
                .requalified());

        mark(id);

        assertTrue(ChecklistViewAssembler.hazards(h.checklists.findById(id).orElseThrow(), h.groups.findAll(id))
                .requalified());
    }
}
