package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.policy.checklist.EarlierHazard;
import vn.sunvibe.survey.core.domain.policy.checklist.Editability;
import vn.sunvibe.survey.core.domain.policy.checklist.LocationDifference;
import vn.sunvibe.survey.core.domain.policy.checklist.Readiness;
import vn.sunvibe.survey.core.domain.policy.checklist.SpaceTerms;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A checklist as a reader of its lead sees it (survey-checklist spec 7.2 {@code ChecklistResponse}): the checklist,
 * the lead's code and status, the lead's staff (only for a viewer who can edit, spec A-25), the visit, the viewer's
 * editability, the seven groups as stored, the computed parts (address difference, readiness, effective terms, earlier
 * hazards), the owner's form, takeovers, corrections, editors, the latest change and one name map for every staff id
 * the answer holds. Private survey data: never logged.
 */
public record ChecklistView(SurveyChecklist checklist, String requestCode, String leadStatus, List<String> leadStaff,
                            SurveyAppointment visit, Editability editability, Map<ChecklistGroup, GroupState> groups,
                            LocationDifference difference, Readiness readiness, List<SpaceTerms> effectiveTerms,
                            List<EarlierHazard> earlierHazards, OwnerFormFacts ownerFormOrNull,
                            List<ChecklistTakeover> takeovers, List<CorrectionView> corrections,
                            List<EditorView> editors, Instant updatedAt, Map<String, String> staffNames) {

    /** The name of a staff id, or null when the id is null or has no name. */
    public String nameOf(String userId) {
        return userId == null ? null : staffNames.get(userId);
    }

    @Override
    public String toString() {
        return "ChecklistView[" + checklist.getId() + "]";
    }
}
