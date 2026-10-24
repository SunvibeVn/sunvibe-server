package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/**
 * G6 (survey-checklist spec 4.6, BR-11 G6): an inventory of shared items, per-space furnishing and items, and other
 * items. Unknown items never block and never stand in for the checks of G2, G4 and G5.
 */
public record AmenitiesContent(List<AmenityItem> sharedItems, List<AmenitiesSpace> spaces,
                               List<OtherItem> otherItems) implements GroupContent {

    @Override
    public ChecklistGroup group() {
        return ChecklistGroup.AMENITIES;
    }

    @Override
    public String toString() {
        return "AmenitiesContent[" + (spaces == null ? 0 : spaces.size()) + " spaces]";
    }
}
