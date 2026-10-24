package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** The basis of a takeover, intake declaration or correction. */
@Schema(description = "The basis of a takeover, intake declaration or correction")
public record ChecklistBasis(
        LocalDate confirmedOn,
        String text) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistBasis[" + confirmedOn + "]";
    }
}
