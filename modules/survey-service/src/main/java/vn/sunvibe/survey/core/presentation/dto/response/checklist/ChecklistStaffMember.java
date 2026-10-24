package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** A staff member of the lead who may be named as the receiver of an owner statement (survey-checklist spec A-25). */
@Schema(description = "A staff member of the lead who may be named as the receiver of an owner statement")
public record ChecklistStaffMember(
        String userId,
        @Schema(nullable = true)
        String name) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistStaffMember[" + userId + "]";
    }
}
