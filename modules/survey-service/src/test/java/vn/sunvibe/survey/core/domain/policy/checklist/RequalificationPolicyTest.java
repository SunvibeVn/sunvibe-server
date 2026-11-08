package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.ASSIGNED;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.CONTACTED;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.DUPLICATE;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.NEW;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.ON_HOLD;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.QUALIFIED;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.REJECTED;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.SURVEY_SCHEDULED;
import static vn.sunvibe.survey.core.domain.model.lead.LeadStatus.WITHDRAWN;

/** Who can withdraw a PASSED now, as the block says it (property-create requalification spec 7, S-12). */
class RequalificationPolicyTest {

    private static final Instant T0 = Instant.parse("2026-11-04T02:00:00Z");
    private static final String ID = "01JCHECK000000000000000001";

    private static SurveyChecklist passed() {
        SurveyChecklist checklist = SurveyChecklist.start(SurveyChecklistId.of(ID), "01JLEAD0000000000000000001", 1,
                SurveyAppointmentId.of("01JV1S1T000000000000000001"), 1, "01JSA1E0000000000000000001", T0);
        checklist.submit("01JSA1E0000000000000000001", T0);
        checklist.decide(DecisionOutcome.PASSED, T0);
        checklist.markPersisted(7L);
        return checklist;
    }

    @Test
    void leadAllows_qualifiedAndHeldOnly() {
        for (LeadStatus status : List.of(QUALIFIED, ON_HOLD)) {
            assertTrue(RequalificationPolicy.leadAllows(status), status.name());
        }
        for (LeadStatus status : List.of(NEW, ASSIGNED, CONTACTED, SURVEY_SCHEDULED, DUPLICATE, REJECTED,
                WITHDRAWN)) {
            assertFalse(RequalificationPolicy.leadAllows(status), status.name());
        }
    }

    @Test
    void state_managerOnACurrentPassed_namesTheRevisionAndItsVersion() {
        assertEquals(new RequalificationState(true, ID, 7L),
                RequalificationPolicy.state(true, Optional.of(passed()), QUALIFIED));
        assertEquals(new RequalificationState(true, ID, 7L),
                RequalificationPolicy.state(true, Optional.of(passed()), ON_HOLD));
    }

    /** SV-020: a field that can be null says so, and a refused viewer learns no id. */
    @Test
    void state_refused_hasNoIdAndNoVersion() {
        RequalificationState refused = new RequalificationState(false, null, null);

        assertEquals(refused, RequalificationPolicy.state(false, Optional.of(passed()), QUALIFIED));
        assertEquals(refused, RequalificationPolicy.state(true, Optional.empty(), QUALIFIED));
        assertEquals(refused, RequalificationPolicy.state(true, Optional.of(passed()), CONTACTED));
    }
}
