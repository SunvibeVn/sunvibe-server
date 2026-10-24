package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Earlier risks through the whole chain of checklists of one lead and intent cycle (survey-checklist BR-18, BR-24,
 * spec A-20). An earlier hazard H (IMPORTANT or UNCLASSIFIED, not OBSERVED_RESOLVED) of checklist k is addressed as of
 * checklist n by a hazard L of a checklist j, k &lt; j &le; n, that links it and is OBSERVED_RESOLVED (RESOLVED, any
 * severity) or IMPORTANT or UNCLASSIFIED and not OBSERVED_RESOLVED (CARRIED: L warns in turn). A minor link that is not
 * seen resolved, or no link, addresses nothing. Computed from the stored content on every read.
 */
public final class EarlierHazardChain {

    private EarlierHazardChain() {
    }

    /**
     * @param earlierOfCycleAscending the checklists of the cycle numbered below the read one, lowest first
     * @param current                 the read checklist
     */
    public static List<EarlierHazard> of(List<ChecklistHazards> earlierOfCycleAscending, ChecklistHazards current) {
        List<ChecklistHazards> chain = new ArrayList<>(earlierOfCycleAscending);
        chain.add(current);
        List<EarlierHazard> earlier = new ArrayList<>();
        for (int k = 0; k < earlierOfCycleAscending.size(); k++) {
            ChecklistHazards checklist = chain.get(k);
            for (Hazard hazard : checklist.hazards()) {
                if (!warns(hazard)) {
                    continue;
                }
                HazardLink self = new HazardLink(checklist.checklistId(), hazard.hazardId());
                List<AddressedBy> addressedBy = new ArrayList<>();
                for (ChecklistHazards later : chain.subList(k + 1, chain.size())) {
                    for (Hazard link : later.hazards()) {
                        if (self.equals(link.previous())) {
                            AddressKind kind = link.status() == HazardStatus.OBSERVED_RESOLVED ? AddressKind.RESOLVED
                                    : warns(link) ? AddressKind.CARRIED : null;
                            if (kind != null) {
                                addressedBy.add(new AddressedBy(later.checklistId(), later.checklistNo(),
                                        link.hazardId(), kind));
                            }
                        }
                    }
                }
                earlier.add(new EarlierHazard(checklist.checklistId(), checklist.checklistNo(), hazard,
                        List.copyOf(addressedBy)));
            }
        }
        return earlier;
    }

    /** IMPORTANT or UNCLASSIFIED (counts as important) and not seen resolved (BR-18). */
    static boolean warns(Hazard hazard) {
        return hazard.severity() != HazardSeverity.MINOR && hazard.status() != HazardStatus.OBSERVED_RESOLVED;
    }
}
