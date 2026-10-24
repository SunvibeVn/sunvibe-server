package vn.sunvibe.survey.core.domain.model.checklist;

import java.time.LocalDate;

/**
 * G4 values of one space (survey-checklist spec 4.6): occupancy seen on the arrival date, the dates as sourced
 * information (a date never sets occupancy, BR-26) and the area checks of the main space, kitchen and bathroom.
 */
public record ConditionSpace(String spaceId, Field<Occupancy> occupancy, SourcedField<LocalDate> expectedVacantFrom,
                             SourcedField<LocalDate> availableFrom, Field<AreaCheck> mainSpace,
                             Field<AreaCheck> kitchen, Field<AreaCheck> bathroom) {

    @Override
    public String toString() {
        return "ConditionSpace[" + spaceId + "]";
    }
}
