package vn.sunvibe.survey.core.presentation.dto.response.media;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why another uploader's committed photo was removed and on what basis (survey-media spec 4.7, BR-12). */
@Schema(description = "The correction of a photo removal")
public record SurveyMediaCorrection(String reason, Basis basis) {

    /** The Vietnam date of the confirmation and its text. */
    @Schema(name = "SurveyMediaCorrectionBasis", description = "The basis of a photo removal's correction")
    public record Basis(LocalDate confirmedOn, String text) {

        /** Staff text: never print it. */
        @Override
        public String toString() {
            return "Basis[" + confirmedOn + "]";
        }
    }

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "SurveyMediaCorrection[hidden]";
    }
}
