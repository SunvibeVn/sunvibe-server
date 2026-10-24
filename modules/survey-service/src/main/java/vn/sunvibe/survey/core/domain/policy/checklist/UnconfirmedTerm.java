package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;

/** A rental term or availability date still UNKNOWN (survey-checklist spec 4.7, US-2 AC-2). */
public record UnconfirmedTerm(ChecklistGroup group, String spaceIdOrNull, String field) {
}
