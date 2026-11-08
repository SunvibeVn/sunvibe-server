package vn.sunvibe.survey.core.application.service.checklist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.command.checklist.RequalifyCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.common.InvalidSurveyStateException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.common.SurveyNotAllowedException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistRequalifyResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveEvent;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveRefusedException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.RequalificationKind;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.MANAGER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;

/**
 * The check order and the writes of a requalification (property-create requalification spec 5.3): each rule comes before
 * the next one, which a revision breaking both shows, and the writes follow the one lock order.
 */
class SurveyChecklistRequalificationCommandServiceTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();
    private SurveyChecklistId id;
    private long version;

    @BeforeEach
    void passed() {
        id = h.started(LEAD, SALE_A).view().checklist().getId();
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.PASSED, null, null, LEADER);
        h.lead(LEAD, "QUALIFIED", SALE_A);
        version = stored().getVersion();
        h.journal.clear();
    }

    private SurveyChecklist stored() {
        return h.checklists.findById(id).orElseThrow();
    }

    private static LocalDate today() {
        return SurveyTimeRules.vietnamDate(Instant.now());
    }

    private RequalifyCommand command(Long expectedVersion, LocalDate basisDate) {
        return new RequalifyCommand(id.getValue(), expectedVersion, RequalificationKind.WRONG_OWNER, "Sai chủ nhà",
                "Chủ thật gọi điện", "Toàn bộ", basisDate);
    }

    private ChecklistRequalifyResult mark() {
        return h.requalification.mark(all(MANAGER), command(version, today()));
    }

    @Test
    void mark_qualified_writesInTheLockOrder() {
        ChecklistRequalifyResult result = mark();

        assertEquals(List.of("lead:update", "checklist:forUpdate", "checklist:update", "requalification:insert",
                "lead:REQUALIFICATION_MARKED"), writesAndLocks());
        assertTrue(stored().isRequalified());
        assertEquals("CONTACTED", result.lead().status());
        assertEquals(LeadMoveEvent.REQUALIFICATION_MARKED, h.leads.moves().get(0).event());
        assertEquals(LeadMoveTarget.CONTACTED, h.leads.moves().get(0).target());
        assertEquals(id.getValue(), h.leads.moves().get(0).referenceId());
        assertNull(h.leads.moves().get(0).detailOrNull());
        ChecklistRequalification row = h.requalifications.all().get(0);
        assertEquals(MANAGER, row.markedByUserId());
        assertEquals("QUALIFIED", row.leadStatusBefore());
        assertEquals("CONTACTED", row.leadStatusAfter());
    }

    @Test
    void mark_onHold_keepsTheLead() {
        h.lead(LEAD, "ON_HOLD", SALE_A);

        ChecklistRequalifyResult result = mark();

        assertEquals("ON_HOLD", result.lead().status());
        assertEquals(LeadMoveTarget.KEEP, h.leads.moves().get(0).target());
        assertEquals("ON_HOLD", h.requalifications.all().get(0).leadStatusAfter());
    }

    @Test
    void mark_body_beforeAnyLookup() {
        assertThrows(ChecklistValidationException.class, () -> h.requalification.mark(all(MANAGER),
                new RequalifyCommand(id.getValue(), version, null, " ", null, "Toàn bộ", null)));

        assertEquals(List.of(), h.journal);
    }

    @Test
    void mark_everyBodyViolation_atOnce() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> h.requalification.mark(all(MANAGER), new RequalifyCommand(id.getValue(), null, null, null, null,
                        null, null)));

        assertEquals(List.of("basisDate", "eventSource", "expectedVersion", "reason", "reasonKind", "scope"),
                refused.violations().stream().map(violation -> violation.path()).sorted().toList());
    }

    @Test
    void mark_unknownRevision_notFound() {
        assertThrows(ChecklistNotFoundException.class, () -> h.requalification.mark(all(MANAGER),
                new RequalifyCommand("01JCHECK000000000000000099", version, RequalificationKind.OTHER, "Lý do", "Nguồn",
                        "Phạm vi", today())));
    }

    @Test
    void mark_version_beforeTheRevisionRule() {
        SurveyChecklist marked = stored();
        marked.markRequalified(Instant.now());
        h.checklists.update(marked);

        assertThrows(SurveyConcurrentModificationException.class, () -> h.requalification.mark(all(MANAGER),
                command(version, today())));
    }

    @Test
    void mark_revisionRule_beforeTheLeadStatus() {
        h.lead(LEAD, "WITHDRAWN", SALE_A);
        SurveyChecklist marked = stored();
        marked.markRequalified(Instant.now());
        h.checklists.update(marked);

        assertThrows(SurveyNotAllowedException.class, () -> h.requalification.mark(all(MANAGER),
                command(marked.getVersion(), today())));
    }

    @Test
    void mark_leadStatus_beforeTheDate() {
        h.lead(LEAD, "WITHDRAWN", SALE_A);

        assertThrows(InvalidSurveyStateException.class, () -> h.requalification.mark(all(MANAGER),
                command(version, today().minusDays(30))));
    }

    @Test
    void mark_dateFromTheDecisionDay() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> h.requalification.mark(all(MANAGER), command(version, today().minusDays(1))));

        assertEquals("basisDate", refused.violations().get(0).path());
        assertFalse(stored().isRequalified());
    }

    /** spec 5.3: a move the lead refuses or a version conflict answers 409, never 500. */
    @Test
    void mark_leadVersionConflict_isAConcurrentChange() {
        h.leads.failNextApply = new LeadVersionConflictException();

        assertThrows(SurveyConcurrentModificationException.class, this::mark);
    }

    @Test
    void mark_leadRefusesTheMove_isAStateRefusal() {
        h.leads.failNextApply = new LeadMoveRefusedException("CONTACTED");

        assertThrows(InvalidSurveyStateException.class, this::mark);
    }

    private List<String> writesAndLocks() {
        return h.journal.stream().filter(entry -> entry.equals("lead:update") || entry.equals("checklist:forUpdate")
                || entry.equals("checklist:update") || entry.equals("requalification:insert")
                || entry.startsWith("lead:REQUALIFICATION")).toList();
    }
}
