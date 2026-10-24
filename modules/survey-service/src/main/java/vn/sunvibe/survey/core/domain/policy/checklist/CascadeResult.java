package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The next content of each group a G1 save changes, in group order; groups it leaves unchanged are absent. */
public record CascadeResult(Map<ChecklistGroup, GroupContent> changed) {

    public CascadeResult {
        changed = Collections.unmodifiableMap(new LinkedHashMap<>(changed));
    }
}
