package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistKind;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.policy.checklist.ReadOnlyReason;

import java.time.Instant;
import java.util.List;

/** A survey checklist as a reader of its lead sees it (survey-checklist spec 7.2). */
@Schema(description = "A survey checklist as a reader of its lead sees it")
public record ChecklistResponse(
        String id,
        String leadId,
        ChecklistLeadSummary lead,
        @Schema(description = "Empty unless the viewer can edit")
        List<ChecklistStaffMember> leadStaff,
        int checklistNo,
        ChecklistStatus status,
        ChecklistKind kind,
        String templateCode,
        int templateVersion,
        ChecklistVisit visit,
        String holderUserId,
        @Schema(nullable = true)
        String holderName,
        boolean editable,
        @Schema(nullable = true)
        ReadOnlyReason readOnlyReason,
        ChecklistGroups groups,
        ChecklistReadiness readiness,
        List<ChecklistSpaceTerms> effectiveTerms,
        List<ChecklistEarlierHazard> earlierHazards,
        @Schema(nullable = true)
        ChecklistOwnerForm ownerForm,
        List<ChecklistTakeover> takeovers,
        List<ChecklistCorrection> corrections,
        List<ChecklistEditor> editors,
        String createdByUserId,
        @Schema(nullable = true)
        String createdByName,
        Instant createdAt,
        long version,
        @Schema(description = "The latest group save or takeover, else the creation")
        Instant updatedAt) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistResponse[" + id + "]";
    }
}
