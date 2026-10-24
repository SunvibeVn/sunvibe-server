package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.Hazard;

import java.util.List;

/**
 * An important or unclassified hazard of an earlier checklist of the cycle, not seen resolved there, as recorded, with
 * every later hazard up to the read checklist that addresses it; none means EARLIER_HAZARD_OPEN (spec A-20).
 */
public record EarlierHazard(String checklistId, int checklistNo, Hazard hazard, List<AddressedBy> addressedBy) {
}
