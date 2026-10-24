package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/**
 * G4 (survey-checklist spec 4.6): the shared kitchen and bathroom, asked only in PARTS scope (null otherwise, BR-11
 * G4, D-AI-31), and the per-space condition.
 */
public record ConditionContent(Field<AreaCheck> sharedKitchen, Field<AreaCheck> sharedBathroom,
                               List<ConditionSpace> spaces) implements GroupContent {

    @Override
    public ChecklistGroup group() {
        return ChecklistGroup.CONDITION;
    }

    @Override
    public String toString() {
        return "ConditionContent[" + (spaces == null ? 0 : spaces.size()) + " spaces]";
    }
}
