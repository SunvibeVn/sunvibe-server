package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Earlier risks through the visits of one lead and intent cycle (survey-checklist BR-18, BR-24, spec A-20, D3-12,
 * survey-checklist submission spec 4.6). Hazards of one visit keep one identity across its revisions, so only revisions of earlier
 * visits count, and through each earlier visit only its latest revision, DRAFT included. An earlier hazard H (IMPORTANT
 * or UNCLASSIFIED, not OBSERVED_RESOLVED) of a visit is addressed as of the read checklist by a later hazard L that
 * links it and is OBSERVED_RESOLVED (RESOLVED, any severity) or IMPORTANT or UNCLASSIFIED and not OBSERVED_RESOLVED
 * (CARRIED: L warns in turn). A link names a visit and a hazard id: one to a non-latest revision of a visit reaches the
 * same hazard of its latest revision. A minor link that is not seen resolved, or no link, addresses nothing. A RESOLVED
 * link on a requalified revision before the read one addresses nothing either: a hazard resolved on the wrong asset was
 * never seen on the right one, so it warns again (property-create requalification D5-19); the marked revision's own read
 * keeps its links. Computed from the stored content on every read.
 */
public final class EarlierHazardChain {

    private EarlierHazardChain() {
    }

    /**
     * @param earlierOfCycleAscending the checklists of the cycle numbered below the read one, lowest first
     * @param current                 the read checklist
     */
    public static List<EarlierHazard> of(List<ChecklistHazards> earlierOfCycleAscending, ChecklistHazards current) {
        Map<String, String> visitOf = new HashMap<>();
        earlierOfCycleAscending.forEach(checklist -> visitOf.put(checklist.checklistId(), checklist.visitId()));
        visitOf.put(current.checklistId(), current.visitId());
        List<ChecklistHazards> earlierVisits = latestOfEarlierVisits(earlierOfCycleAscending, current.visitId());
        List<ChecklistHazards> chain = new ArrayList<>(earlierVisits);
        chain.add(current);
        List<EarlierHazard> earlier = new ArrayList<>();
        for (int k = 0; k < earlierVisits.size(); k++) {
            ChecklistHazards checklist = chain.get(k);
            for (Hazard hazard : checklist.hazards()) {
                if (!warns(hazard)) {
                    continue;
                }
                List<AddressedBy> addressedBy = new ArrayList<>();
                for (ChecklistHazards later : chain.subList(k + 1, chain.size())) {
                    boolean withdrawn = later != current && later.requalified();
                    for (Hazard link : later.hazards()) {
                        if (links(link.previous(), checklist.visitId(), hazard.hazardId(), visitOf)) {
                            AddressKind kind = addressKind(link, withdrawn);
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

    /**
     * What a {@code previous} link may name (spec 4.6): a revision of an earlier visit, also a non-latest one; a
     * revision of the same visit is the same hazard, not an earlier one.
     */
    public static List<ChecklistHazards> linkTargets(List<ChecklistHazards> earlierOfCycle, String ownVisitId) {
        return earlierOfCycle.stream().filter(checklist -> !checklist.visitId().equals(ownVisitId)).toList();
    }

    private static AddressKind addressKind(Hazard link, boolean onAWithdrawnRevision) {
        if (link.status() == HazardStatus.OBSERVED_RESOLVED) {
            return onAWithdrawnRevision ? null : AddressKind.RESOLVED;
        }
        return warns(link) ? AddressKind.CARRIED : null;
    }

    /** IMPORTANT or UNCLASSIFIED (counts as important) and not seen resolved (BR-18). */
    static boolean warns(Hazard hazard) {
        return hazard.severity() != HazardSeverity.MINOR && hazard.status() != HazardStatus.OBSERVED_RESOLVED;
    }

    private static List<ChecklistHazards> latestOfEarlierVisits(List<ChecklistHazards> earlierOfCycleAscending,
                                                                String ownVisitId) {
        Map<String, ChecklistHazards> latest = new LinkedHashMap<>();
        for (ChecklistHazards checklist : linkTargets(earlierOfCycleAscending, ownVisitId)) {
            latest.merge(checklist.visitId(), checklist, (kept, next) ->
                    next.checklistNo() > kept.checklistNo() ? next : kept);
        }
        return latest.values().stream().sorted(Comparator.comparingInt(ChecklistHazards::checklistNo)).toList();
    }

    private static boolean links(HazardLink link, String visitId, String hazardId, Map<String, String> visitOf) {
        return link != null && hazardId.equals(link.hazardId()) && visitId.equals(visitOf.get(link.checklistId()));
    }
}
