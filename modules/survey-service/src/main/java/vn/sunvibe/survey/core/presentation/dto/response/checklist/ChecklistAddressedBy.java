package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.AddressKind;

/** A later hazard that addresses an earlier one (survey-checklist spec A-20). */
@Schema(description = "A later hazard that addresses an earlier one")
public record ChecklistAddressedBy(
        String checklistId,
        int checklistNo,
        String hazardId,
        AddressKind kind) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAddressedBy[" + hazardId + ", " + kind + "]";
    }
}
