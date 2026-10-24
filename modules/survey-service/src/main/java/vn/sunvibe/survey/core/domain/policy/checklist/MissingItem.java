package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;

/** One known gap before a full-assessment submission (survey-checklist spec 4.7). */
public record MissingItem(ChecklistGroup group, String spaceIdOrNull, String fieldOrNull, MissingProblem problem) {
}
