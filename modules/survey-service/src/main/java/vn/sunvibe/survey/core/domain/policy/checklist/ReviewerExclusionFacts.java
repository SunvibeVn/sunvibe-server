package vn.sunvibe.survey.core.domain.policy.checklist;

import java.util.Set;

/**
 * Who touched a revision, from stored rows of every revision on its visit up to it (survey-checklist submission spec
 * 4.2, plan PD-2): the visit's performer and recorder, every holder (creators and every takeover or intake target),
 * every submitter, every user in the save logs, every uploader (removed photos included) and everyone who removed or
 * deselected a photo there.
 */
public record ReviewerExclusionFacts(String performerUserId, String recorderUserId, Set<String> holders,
                                     Set<String> submitters, Set<String> savers, Set<String> uploaders,
                                     Set<String> removers, Set<String> deselectors) {

    public ReviewerExclusionFacts {
        holders = Set.copyOf(holders);
        submitters = Set.copyOf(submitters);
        savers = Set.copyOf(savers);
        uploaders = Set.copyOf(uploaders);
        removers = Set.copyOf(removers);
        deselectors = Set.copyOf(deselectors);
    }
}
