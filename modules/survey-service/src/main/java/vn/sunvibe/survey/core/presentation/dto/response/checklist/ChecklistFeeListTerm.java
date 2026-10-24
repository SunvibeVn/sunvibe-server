package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;

import java.time.LocalDate;
import java.util.List;

/** Fee list term; VALUE with an empty list means none of a survey checklist, as read (survey-checklist spec 7.2). */
@Schema(description = "Fee list term; VALUE with an empty list means none of a survey checklist, as read")
public record ChecklistFeeListTerm(
        FieldState state,
        @Schema(nullable = true, description = "Present exactly when state is VALUE")
        List<ChecklistFee> value,
        @Schema(nullable = true, description = "Present exactly when state is NOT_APPLICABLE, 1..500 characters")
        String naReason,
        @Schema(nullable = true, description = "Required with VALUE")
        ChecklistTermsSource source,
        @Schema(nullable = true, description = "OWNER_STATED only: the day the owner said it")
        LocalDate confirmedOn,
        @Schema(nullable = true, description = "OWNER_STATED only: who heard it, one of the lead's staff")
        String receivedByUserId,
        @Schema(nullable = true, description = "OWNER_STATED only, exactly when someone else heard it: how it reached "
                + "the person entering it")
        String relayBasis,
        @Schema(nullable = true, description = "Null until the unit is first changed")
        ChecklistUnitMeta meta) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistFeeListTerm[" + state + "]";
    }
}
