package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.Hazard;

import java.util.List;

/** The stored hazards of one checklist of the cycle (empty when its list is unknown), with its visit. */
public record ChecklistHazards(String checklistId, String visitId, int checklistNo, List<Hazard> hazards) {
}
