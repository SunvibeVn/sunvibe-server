package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/** G2 (survey-checklist spec 4.6): access route, shared areas, total floors and the per-space parts. */
public record PartsContent(Field<String> accessRoute, Field<String> sharedAreas, SourcedField<Integer> totalFloors,
                           List<PartsSpace> spaces) implements GroupContent {

    @Override
    public ChecklistGroup group() {
        return ChecklistGroup.PARTS;
    }

    @Override
    public String toString() {
        return "PartsContent[" + (spaces == null ? 0 : spaces.size()) + " spaces]";
    }
}
