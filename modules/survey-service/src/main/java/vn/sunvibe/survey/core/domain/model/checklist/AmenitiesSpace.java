package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/** G6 values of one space: furnishing and the checked items of the space (survey-checklist spec 4.6). */
public record AmenitiesSpace(String spaceId, SourcedField<Furnishing> furnishing, List<AmenityItem> items) {

    @Override
    public String toString() {
        return "AmenitiesSpace[" + spaceId + "]";
    }
}
