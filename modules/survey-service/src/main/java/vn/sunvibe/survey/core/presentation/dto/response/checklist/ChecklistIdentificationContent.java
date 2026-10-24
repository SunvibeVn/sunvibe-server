package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;

import java.util.List;

/** G1 identification, as read (survey-checklist spec 7.1, 7.2). */
@Schema(description = "G1 identification, as read")
public record ChecklistIdentificationContent(
        ChecklistLocationField observedAddress,
        @Schema(nullable = true)
        String differenceNote,
        ChecklistPropertyTypeField propertyType,
        ChecklistFitField fit,
        @Schema(nullable = true)
        RentalScope rentalScope,
        List<ChecklistObservedSpace> spaces,
        @Schema(nullable = true)
        String otherPartsNote,
        @Schema(nullable = true)
        String generalNote,
        ChecklistAddressDifference difference,
        @Schema(nullable = true, description = "The catalog name of the observed province; null while the address is "
                + "unknown")
        String provinceName,
        @Schema(nullable = true, description = "The catalog name of the observed ward; null while the address is "
                + "unknown")
        String wardName,
        ChecklistIdentificationFieldMeta fieldMeta) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistIdentificationContent[hidden]";
    }
}
