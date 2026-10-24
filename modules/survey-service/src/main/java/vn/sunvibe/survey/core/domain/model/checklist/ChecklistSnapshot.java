package vn.sunvibe.survey.core.domain.model.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;

import java.util.EnumMap;
import java.util.Map;

/**
 * A takeover checkpoint (survey-checklist spec 4.5, BR-14): every stored group with its version, provenance and last
 * save, as it was just before the takeover. Private survey data, read only through its endpoint.
 */
public record ChecklistSnapshot(Map<ChecklistGroup, GroupState> groups) {

    public ChecklistSnapshot {
        if (groups == null || !groups.keySet().containsAll(ChecklistGroup.stored())) {
            throw new IllegalArgumentException("A checkpoint holds every stored group.");
        }
        groups = java.util.Collections.unmodifiableMap(new EnumMap<>(groups));
    }

    @Override
    public String toString() {
        return "ChecklistSnapshot[hidden]";
    }
}
