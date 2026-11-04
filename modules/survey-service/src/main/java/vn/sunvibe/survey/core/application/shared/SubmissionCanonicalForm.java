package vn.sunvibe.survey.core.application.shared;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;

import java.util.List;
import java.util.Map;

/**
 * The canonical JSON a submission's hash is taken over (survey-checklist submission plan PD-1): {@code {"evidence":
 * [...], "groups": {<GROUP_KEY>: <stored content>}}} with sorted keys, entries sorted by photo id, nulls written and
 * numbers as stored. A port, so the JSON library stays out of the use case.
 */
public interface SubmissionCanonicalForm {

    String of(Map<ChecklistGroup, GroupContent> groups, List<EvidenceEntry> evidence);
}
