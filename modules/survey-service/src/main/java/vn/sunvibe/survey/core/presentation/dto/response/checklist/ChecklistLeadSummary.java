package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The checklist's lead: its request code and status. */
@Schema(description = "The checklist's lead: its request code and status")
public record ChecklistLeadSummary(
        String requestCode,
        String status) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistLeadSummary[" + status + "]";
    }
}
