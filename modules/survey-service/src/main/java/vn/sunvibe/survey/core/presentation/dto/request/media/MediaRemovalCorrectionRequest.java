package vn.sunvibe.survey.core.presentation.dto.request.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Why a holder removes a photo another person uploaded, and on what basis (survey-media spec 4.7, BR-12; the shape of
 * survey-checklist BR-22): a reason and a basis with its Vietnam date and text.
 */
@Schema(description = "Why a photo another person uploaded is removed, and on what basis")
public record MediaRemovalCorrectionRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters, no phone number")
        String reason,

        @JsonProperty(required = true)
        @Schema(nullable = true)
        Basis basis) {

    /** The basis: the Vietnam date of the confirmation, from the visit's arrival date to today, and a short text. */
    @Schema(name = "MediaRemovalBasisRequest", description = "The basis of a removal correction")
    public record Basis(
            @JsonProperty(required = true)
            @Schema(nullable = true, description = "Vietnam date, from the visit's arrival date to today")
            LocalDate confirmedOn,

            @JsonProperty(required = true)
            @Schema(nullable = true, description = "1..500 characters, no phone number")
            String text) {

        /** Staff text: never print it. */
        @Override
        public String toString() {
            return "Basis[" + confirmedOn + "]";
        }
    }

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "MediaRemovalCorrectionRequest[hidden]";
    }
}
