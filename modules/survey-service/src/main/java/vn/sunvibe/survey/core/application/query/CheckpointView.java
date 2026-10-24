package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.policy.checklist.LocationDifference;

import java.util.Map;

/**
 * A takeover checkpoint (survey-checklist spec 7.2): the takeover and every group as it was just before, the address
 * difference of that G1 (so the groups read like those of the checklist) and the names of the staff ids it holds.
 * Private survey data: never logged.
 */
public record CheckpointView(ChecklistTakeover takeover, Map<ChecklistGroup, GroupState> groups,
                             LocationDifference difference, Map<String, String> staffNames) {

    public String nameOf(String userId) {
        return userId == null ? null : staffNames.get(userId);
    }

    @Override
    public String toString() {
        return "CheckpointView[" + takeover + "]";
    }
}
