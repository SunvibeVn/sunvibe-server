package vn.sunvibe.survey.core.domain.model.checklist;

import java.math.BigDecimal;

/**
 * G3 values of one space (survey-checklist spec 4.6): area in square metres with one decimal, bedrooms, private
 * bathrooms, shared bathroom use and the layout note (a plain authored note, spec A-26).
 */
public record DimensionsSpace(String spaceId, SourcedField<BigDecimal> area, SourcedField<Integer> bedrooms,
                              SourcedField<Integer> privateBathrooms, SourcedField<YesNo> sharedBathroomUse,
                              String layoutNote) {

    @Override
    public String toString() {
        return "DimensionsSpace[" + spaceId + "]";
    }
}
