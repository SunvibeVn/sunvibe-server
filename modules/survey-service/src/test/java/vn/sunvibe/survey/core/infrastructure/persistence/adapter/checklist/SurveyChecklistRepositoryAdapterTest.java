package vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistKind;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

/**
 * A stored checklist keeps every value in its own field through the row and back (cleanup spec A-5, SV-169): each
 * parameter of {@link SurveyChecklist#load} gets a value no other parameter has, so two arguments swapped in the
 * adapter's mapping or in {@code load} fail here.
 */
class SurveyChecklistRepositoryAdapterTest {

    private static final String ID = "01JB0000000000000000000001";
    private static final String VISIT_ID = "01JB0000000000000000000002";
    private static final String PREVIOUS_ID = "01JB0000000000000000000003";

    @Test
    void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
        SurveyChecklist stored = SurveyChecklist.load(SurveyChecklistId.of(ID), "lead-1", 2,
                SurveyAppointmentId.of(VISIT_ID), 3, "holder-1", "template-1", 4, ChecklistKind.FULL_ASSESSMENT,
                ChecklistStatus.NEEDS_CHANGES, "creator-1", at(1), SurveyChecklistId.of(PREVIOUS_ID), at(2),
                "submitter-1", at(3), at(4), 5L);

        SurveyChecklist back = SurveyChecklistRepositoryAdapter.toDomain(
                SurveyChecklistRepositoryAdapter.toEntity(stored));

        assertEquals(ID, back.getId().getValue());
        assertEquals("lead-1", back.getLeadId());
        assertEquals(2, back.getChecklistNo());
        assertEquals(VISIT_ID, back.getVisitAppointmentId().getValue());
        assertEquals(3, back.getIntentCycle());
        assertEquals("holder-1", back.getHolderUserId());
        assertEquals("template-1", back.getTemplateCode());
        assertEquals(4, back.getTemplateVersion());
        assertEquals(ChecklistKind.FULL_ASSESSMENT, back.getKind());
        assertEquals(ChecklistStatus.NEEDS_CHANGES, back.getStatus());
        assertEquals("creator-1", back.getCreatedBy());
        assertEquals(at(1), back.getCreatedAt());
        assertEquals(PREVIOUS_ID, back.getPreviousChecklistIdOrNull().getValue());
        assertEquals(at(2), back.getSubmittedAt());
        assertEquals("submitter-1", back.getSubmittedByUserId());
        assertEquals(at(3), back.getDecidedAt());
        assertEquals(at(4), back.getRequalifiedAt());
        assertEquals(5L, back.getVersion());
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-11-01T01:00:00Z").plusSeconds(60L * minute);
    }
}
