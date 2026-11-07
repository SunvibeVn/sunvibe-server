package vn.sunvibe.survey.core.application.shared.checklist;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewerExclusionFacts;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistSaveDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSelectionDomainRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The stored facts of who touched a revision (survey-checklist submission spec 4.2, plan PD-2): from every revision on
 * its visit up to it, in a fixed number of queries however many revisions are asked about. One path for a decision, a
 * read and the review queue, so they cannot disagree.
 */
@Component
public class ReviewerExclusionLoader {

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistTakeoverDomainRepository takeovers;
    private final SurveyChecklistSaveDomainRepository saves;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSelectionDomainRepository selections;

    public ReviewerExclusionLoader(SurveyChecklistDomainRepository checklists,
                                   SurveyChecklistTakeoverDomainRepository takeovers,
                                   SurveyChecklistSaveDomainRepository saves, SurveyMediaDomainRepository media,
                                   SurveyMediaSelectionDomainRepository selections) {
        this.checklists = checklists;
        this.takeovers = takeovers;
        this.saves = saves;
        this.media = media;
        this.selections = selections;
    }

    /** A revision and the visit it belongs to. */
    public record Target(SurveyChecklist revision, SurveyAppointment visit) {
    }

    public ReviewerExclusionFacts load(SurveyChecklist revision, SurveyAppointment visit) {
        return loadAll(List.of(new Target(revision, visit))).get(revision.getId());
    }

    /** The facts of each revision, by id. */
    public Map<SurveyChecklistId, ReviewerExclusionFacts> loadAll(Collection<Target> targets) {
        if (targets.isEmpty()) {
            return Map.of();
        }
        Set<SurveyAppointmentId> visitIds = new HashSet<>();
        targets.forEach(target -> visitIds.add(target.visit().getId()));
        List<SurveyChecklist> onTheVisits = checklists.findByVisitAppointmentIds(visitIds);
        List<SurveyChecklistId> ids = onTheVisits.stream().map(SurveyChecklist::getId).toList();
        List<String> idValues = ids.stream().map(SurveyChecklistId::getValue).toList();
        Map<String, Set<String>> targetsOf = byChecklist(takeovers.findTargets(ids));
        Map<String, Set<String>> saversOf = byChecklist(saves.findActors(ids));
        Map<String, Set<String>> uploadersOf = byChecklist(media.findUploaders(idValues));
        Map<String, Set<String>> removersOf = byChecklist(media.findRemovers(idValues));
        Map<String, Set<String>> deselectorsOf = new HashMap<>();
        for (MediaSelection selection : selections.findByChecklistIds(idValues)) {
            if (!selection.isSelected()) {
                deselectorsOf.computeIfAbsent(selection.checklistId(), id -> new HashSet<>())
                        .add(selection.deselectedByUserIdOrNull());
            }
        }
        Map<SurveyChecklistId, ReviewerExclusionFacts> facts = new LinkedHashMap<>();
        for (Target target : targets) {
            List<SurveyChecklist> upToIt = onTheVisits.stream()
                    .filter(other -> other.getVisitAppointmentId().equals(target.visit().getId())
                            && other.getChecklistNo() <= target.revision().getChecklistNo())
                    .toList();
            Set<String> holders = new HashSet<>(union(upToIt, targetsOf));
            Set<String> submitters = new HashSet<>();
            for (SurveyChecklist revision : upToIt) {
                holders.add(revision.getCreatedBy());
                holders.add(revision.getHolderUserId());
                if (revision.getSubmittedByUserId() != null) {
                    submitters.add(revision.getSubmittedByUserId());
                }
            }
            VisitRecord record = target.visit().getVisit();
            facts.put(target.revision().getId(), new ReviewerExclusionFacts(record.performedBy(), record.recordedBy(),
                    holders, submitters, union(upToIt, saversOf), union(upToIt, uploadersOf),
                    union(upToIt, removersOf), union(upToIt, deselectorsOf)));
        }
        return facts;
    }

    private static Map<String, Set<String>> byChecklist(List<ChecklistPerson> people) {
        Map<String, Set<String>> byChecklist = new HashMap<>();
        people.forEach(person -> byChecklist.computeIfAbsent(person.checklistId(), id -> new HashSet<>())
                .add(person.userId()));
        return byChecklist;
    }

    private static Set<String> union(List<SurveyChecklist> revisions, Map<String, Set<String>> byChecklist) {
        Function<SurveyChecklist, Set<String>> of = revision -> byChecklist.getOrDefault(revision.getId().getValue(),
                Set.of());
        Set<String> all = new HashSet<>();
        revisions.forEach(revision -> all.addAll(of.apply(revision)));
        return all;
    }
}
