package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/** G3 (survey-checklist spec 4.6): the per-space dimensions. */
public record DimensionsContent(List<DimensionsSpace> spaces) implements GroupContent {

    @Override
    public ChecklistGroup group() {
        return ChecklistGroup.DIMENSIONS;
    }

    @Override
    public String toString() {
        return "DimensionsContent[" + (spaces == null ? 0 : spaces.size()) + " spaces]";
    }
}
