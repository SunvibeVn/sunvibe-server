package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.command.BasisInput;
import vn.sunvibe.survey.core.application.command.StartChecklistCommand;
import vn.sunvibe.survey.core.application.command.TakeOverChecklistCommand;
import vn.sunvibe.survey.core.application.exception.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.query.ChecklistView;
import vn.sunvibe.survey.core.application.query.CheckpointView;
import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.policy.checklist.ReadOnlyReason;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.MANAGER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_B;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.arrivalDate;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.intake;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.own;

/** Reading a checklist and a takeover checkpoint (survey-checklist spec 4.4, 4.10, A-10, A-25, A-27). */
class SurveyChecklistQueryServiceTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();

    @Test
    void read_saleOfAnotherLead_isNotFoundWithAnEvent() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();

        assertThrows(ChecklistNotFoundException.class, () -> h.queries.read(own(SALE_B), checklist.getId().getValue()));

        SurveySecurityEvent event = h.securityEvents.all().get(0);
        assertEquals(SecurityResourceType.CHECKLIST, event.resourceType());
        assertEquals(checklist.getId().getValue(), event.resourceId());
        assertEquals(SecurityAction.READ, event.action());
        assertEquals(SecurityOutcome.NOT_VISIBLE, event.outcome());
    }

    @Test
    void read_unknownOrMalformedId_isNotFoundWithoutAnEvent() {
        h.started(LEAD, SALE_A);

        assertThrows(ChecklistNotFoundException.class, () -> h.queries.read(own(SALE_A), "01JNOSUCHCHECKLIST00000000"));
        assertThrows(ChecklistNotFoundException.class, () -> h.queries.read(own(SALE_A), "nope"));
        assertEquals(List.of(), h.securityEvents.all());
    }

    /** spec 4.4, A-25: a manager reads with NOT_ASSIGNEE and no lead staff. */
    @Test
    void read_managerReadsReadOnlyWithoutLeadStaff() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();

        ChecklistView view = h.queries.read(all(MANAGER), checklist.getId().getValue());

        assertFalse(view.editability().editable());
        assertEquals(ReadOnlyReason.NOT_ASSIGNEE, view.editability().reason());
        assertEquals(List.of(), view.leadStaff());
    }

    /** spec A-25: the holder gets the lead's staff: assignee, History names, performers and recorders, named. */
    @Test
    void read_holderGetsTheLeadStaffWithNames() {
        h.leads.withHistoryStaff(LEAD, LEADER, MANAGER);
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();

        ChecklistView view = h.queries.read(own(SALE_A), checklist.getId().getValue());

        assertEquals(Set.of(SALE_A, LEADER, MANAGER), Set.copyOf(view.leadStaff()));
        assertEquals("Man Ager", view.nameOf(MANAGER));
        assertEquals("Sale A", view.nameOf(SALE_A));
    }

    @Test
    void read_ownerFormOnlyWhenTheLeadHasOne() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();
        assertNull(h.queries.read(own(SALE_A), checklist.getId().getValue()).ownerFormOrNull());

        h.leads.withOwnerForm(LEAD, new OwnerFormFacts(Instant.parse("2026-10-01T03:00:00Z"), "12 Bach Dang", "48",
                "20194", new BigDecimal("16.054407"), new BigDecimal("108.202167"), 15_000_000L,
                LocalDate.parse("2026-11-01")));

        assertEquals(15_000_000L, h.queries.read(own(SALE_A), checklist.getId().getValue()).ownerFormOrNull()
                .monthlyAskingPriceOrNull());
    }

    @Test
    void read_writesOneAccessLogRowWithTheChecklist() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();
        int before = h.accessLogs.all().size();

        h.queries.read(all(LEADER), checklist.getId().getValue());

        SurveyAccessLog log = h.accessLogs.all().get(before);
        assertEquals(before + 1, h.accessLogs.all().size());
        assertEquals(AccessResourceType.CHECKLIST, log.resourceTypeOrNull());
        assertEquals(checklist.getId().getValue(), log.resourceIdOrNull());
        assertEquals(LEADER, log.userId());
    }

    // ==================== checkpoints ====================

    @Test
    void checkpoint_ofATakeover_holdsTheGroupsBeforeAndIsAccessLogged() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        SurveyAppointment visit = h.appointments.all().get(0);
        h.commands.takeOver(own(SALE_B), new TakeOverChecklistCommand(checklist.getId().getValue(), 0L,
                "Được giao lại", new BasisInput(arrivalDate(visit), "Quản lý giao")));

        CheckpointView checkpoint = h.queries.readCheckpoint(own(SALE_B), checklist.getId().getValue(), 1);

        assertEquals(1, checkpoint.takeover().takeoverNo());
        assertEquals(7, checkpoint.groups().size());
        SurveyAccessLog log = h.accessLogs.all().get(h.accessLogs.all().size() - 1);
        assertEquals(AccessResourceType.CHECKPOINT, log.resourceTypeOrNull());
        assertEquals(checklist.getId().getValue() + ":1", log.resourceIdOrNull());
    }

    @Test
    void checkpoint_ofAnIntakeDeclaration_isNotFoundWithoutAnEvent() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        SurveyAppointment visit = h.visited(LEAD, SurveyIntent.LONG_TERM_RENT, 1, SALE_A);
        SurveyChecklist checklist = h.commands.start(own(SALE_B), new StartChecklistCommand(visit.getId().getValue(),
                intake(arrivalDate(visit)))).view().checklist();

        assertThrows(ChecklistNotFoundException.class, () -> h.queries.readCheckpoint(own(SALE_B),
                checklist.getId().getValue(), 1));
        assertThrows(ChecklistNotFoundException.class, () -> h.queries.readCheckpoint(own(SALE_B),
                checklist.getId().getValue(), 7));
        assertEquals(List.of(), h.securityEvents.all());
    }

    /** spec 4.10: a checkpoint read on a hidden lead writes a CHECKPOINT event whatever the number. */
    @Test
    void checkpoint_onAHiddenLead_isNotFoundWithACheckpointEvent() {
        SurveyChecklist checklist = h.started(LEAD, SALE_A).view().checklist();

        assertThrows(ChecklistNotFoundException.class, () -> h.queries.readCheckpoint(own(SALE_B),
                checklist.getId().getValue(), 42));

        SurveySecurityEvent event = h.securityEvents.all().get(0);
        assertEquals(SecurityResourceType.CHECKPOINT, event.resourceType());
        assertEquals(checklist.getId().getValue() + ":42", event.resourceId());
        assertEquals(SecurityAction.READ, event.action());
        assertTrue(h.accessLogs.all().stream().noneMatch(log -> log.userId().equals(SALE_B)));
    }
}
