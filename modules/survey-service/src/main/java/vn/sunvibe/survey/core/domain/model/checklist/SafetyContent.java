package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/**
 * G5 (survey-checklist spec 4.6): ways in and out, access restrictions and the hazard list; an empty list in VALUE
 * means none seen (BR-16).
 */
public record SafetyContent(Field<String> egress, Field<Observation> accessRestrictions,
                            Field<List<Hazard>> hazards) implements GroupContent {

    @Override
    public ChecklistGroup group() {
        return ChecklistGroup.SAFETY;
    }

    @Override
    public String toString() {
        return "SafetyContent[" + hazards + "]";
    }
}
