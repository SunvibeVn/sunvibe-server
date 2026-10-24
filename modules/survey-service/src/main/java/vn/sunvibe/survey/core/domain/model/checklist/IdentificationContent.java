package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/**
 * G1 (survey-checklist spec 4.6): the observed address, the difference note, the asset type, the fit, the rental
 * scope, the observed spaces and the two notes. Rental scope and notes are plain nullable fields (spec A-26).
 */
public record IdentificationContent(SourcedField<ObservedLocation> observedAddress, String differenceNote,
                                    Field<PropertyTypeCode> propertyType, Field<FitAssessment> fit,
                                    RentalScope rentalScope, List<ObservedSpace> spaces, String otherPartsNote,
                                    String generalNote) implements GroupContent {

    @Override
    public ChecklistGroup group() {
        return ChecklistGroup.IDENTIFICATION;
    }

    @Override
    public String toString() {
        return "IdentificationContent[" + (spaces == null ? 0 : spaces.size()) + " spaces]";
    }
}
