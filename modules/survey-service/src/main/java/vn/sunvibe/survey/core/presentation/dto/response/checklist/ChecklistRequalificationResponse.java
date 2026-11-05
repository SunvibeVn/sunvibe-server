package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.RequalificationKind;

import java.time.Instant;
import java.time.LocalDate;

/** Why a PASSED revision was withdrawn from use (property-create requalification spec 4, 7). */
@Schema(description = "Why the PASSED revision was withdrawn from use")
public record ChecklistRequalificationResponse(
        RequalificationKind reasonKind,
        String reason,
        String eventSource,
        String scope,
        LocalDate basisDate,
        ChecklistStaffMember markedBy,
        Instant markedAt) {

    /** The manager's text is private: never print it. */
    @Override
    public String toString() {
        return "ChecklistRequalificationResponse[" + reasonKind + "]";
    }
}
