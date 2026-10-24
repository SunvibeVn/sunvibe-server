package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;

import java.util.Map;

/**
 * Save one group of a draft (survey-checklist spec 7.1): the group version read, for a G1 save the versions of the
 * other groups it changes (spec A-12; empty for other groups), the correction or null, and the whole group content.
 * A null version is refused as a missing value.
 */
public record SaveChecklistGroupCommand(String checklistId, ChecklistGroup group, Long expectedVersion,
                                        Map<ChecklistGroup, Long> otherGroupVersions,
                                        CorrectionInput correctionOrNull, GroupContent content) {

    public SaveChecklistGroupCommand {
        otherGroupVersions = otherGroupVersions == null ? Map.of() : Map.copyOf(otherGroupVersions);
    }

    /** Content is private: never print it. */
    @Override
    public String toString() {
        return "SaveChecklistGroupCommand[" + checklistId + ", " + group + "]";
    }
}
