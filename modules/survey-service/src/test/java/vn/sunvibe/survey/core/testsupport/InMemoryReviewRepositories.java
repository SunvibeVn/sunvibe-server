package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDecisionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistRequalificationDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSubmissionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSelectionDomainRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Hand-written fakes of the submission, evidence, decision and selection rows; each write goes to the journal. */
public final class InMemoryReviewRepositories {

    private InMemoryReviewRepositories() {
    }

    public static class Submissions implements SurveyChecklistSubmissionDomainRepository {

        private final Map<SurveyChecklistId, ChecklistSubmission> stored = new LinkedHashMap<>();
        private final List<String> journal;

        public Submissions(List<String> journal) {
            this.journal = journal;
        }

        @Override
        public void save(ChecklistSubmission submission) {
            journal.add("submission:insert");
            stored.put(submission.checklistId(), submission);
        }

        @Override
        public Optional<ChecklistSubmission> findByChecklistId(SurveyChecklistId checklistId) {
            return Optional.ofNullable(stored.get(checklistId));
        }

        public List<ChecklistSubmission> all() {
            return List.copyOf(stored.values());
        }
    }

    public static class Evidence implements SurveyChecklistEvidenceDomainRepository {

        private final Map<SurveyChecklistId, List<EvidenceEntry>> stored = new LinkedHashMap<>();
        private final List<String> journal;

        public Evidence(List<String> journal) {
            this.journal = journal;
        }

        @Override
        public void saveAll(SurveyChecklistId checklistId, List<EvidenceEntry> entries) {
            journal.add("evidence:insert");
            stored.put(checklistId, List.copyOf(entries));
        }

        @Override
        public List<EvidenceEntry> findByChecklistId(SurveyChecklistId checklistId) {
            return stored.getOrDefault(checklistId, List.of()).stream()
                    .sorted(Comparator.comparing(EvidenceEntry::mediaId)).toList();
        }

        @Override
        public Set<String> findReferencedAmong(Collection<SurveyMediaId> mediaIds) {
            Set<String> wanted = mediaIds.stream().map(SurveyMediaId::getValue).collect(Collectors.toSet());
            return stored.values().stream().flatMap(List::stream).map(EvidenceEntry::mediaId)
                    .filter(wanted::contains).collect(Collectors.toSet());
        }
    }

    public static class Decisions implements SurveyChecklistDecisionDomainRepository {

        private final Map<SurveyChecklistId, ChecklistDecision> stored = new LinkedHashMap<>();
        private final List<String> journal;

        public Decisions(List<String> journal) {
            this.journal = journal;
        }

        @Override
        public void save(ChecklistDecision decision) {
            journal.add("decision:insert");
            stored.put(decision.checklistId(), decision);
        }

        @Override
        public Optional<ChecklistDecision> findByChecklistId(SurveyChecklistId checklistId) {
            return Optional.ofNullable(stored.get(checklistId));
        }

        @Override
        public Map<SurveyChecklistId, ChecklistDecision> findByChecklistIds(Collection<SurveyChecklistId> ids) {
            Map<SurveyChecklistId, ChecklistDecision> found = new LinkedHashMap<>();
            ids.forEach(id -> {
                if (stored.containsKey(id)) {
                    found.put(id, stored.get(id));
                }
            });
            return found;
        }
    }

    public static class Requalifications implements SurveyChecklistRequalificationDomainRepository {

        private final Map<SurveyChecklistId, ChecklistRequalification> stored = new LinkedHashMap<>();
        private final List<String> journal;
        public RuntimeException failNextSave;

        public Requalifications(List<String> journal) {
            this.journal = journal;
        }

        @Override
        public void save(ChecklistRequalification requalification) {
            journal.add("requalification:insert");
            if (failNextSave != null) {
                RuntimeException failure = failNextSave;
                failNextSave = null;
                throw failure;
            }
            if (stored.putIfAbsent(requalification.checklistId(), requalification) != null) {
                throw new IllegalStateException("One requalification per revision.");
            }
        }

        @Override
        public Optional<ChecklistRequalification> findByChecklistId(SurveyChecklistId checklistId) {
            return Optional.ofNullable(stored.get(checklistId));
        }

        public List<ChecklistRequalification> all() {
            return List.copyOf(stored.values());
        }
    }

    public static class Selections implements SurveyMediaSelectionDomainRepository {

        private final List<MediaSelection> stored = new ArrayList<>();
        private final List<String> journal;

        public Selections(List<String> journal) {
            this.journal = journal;
        }

        @Override
        public void insertAll(List<MediaSelection> selections) {
            journal.add("selection:insert");
            stored.addAll(selections);
        }

        @Override
        public void update(MediaSelection selection) {
            journal.add("selection:update");
            stored.removeIf(row -> row.checklistId().equals(selection.checklistId())
                    && row.mediaId().equals(selection.mediaId()));
            stored.add(selection);
        }

        @Override
        public List<MediaSelection> findByChecklistId(String checklistId) {
            return findByChecklistIds(List.of(checklistId));
        }

        @Override
        public Optional<MediaSelection> findOne(String checklistId, SurveyMediaId mediaId) {
            return stored.stream().filter(row -> row.checklistId().equals(checklistId)
                    && row.mediaId().equals(mediaId)).findFirst();
        }

        @Override
        public Optional<String> findCarriedFrom(String checklistId, SurveyMediaId mediaId) {
            return findOne(checklistId, mediaId).map(MediaSelection::carriedFromChecklistId);
        }

        @Override
        public List<String> findSelectingChecklistIds(SurveyMediaId mediaId) {
            return stored.stream().filter(row -> row.mediaId().equals(mediaId) && row.isSelected())
                    .map(MediaSelection::checklistId).sorted().toList();
        }

        @Override
        public List<MediaSelection> findByChecklistIds(Collection<String> checklistIds) {
            return stored.stream().filter(row -> checklistIds.contains(row.checklistId()))
                    .sorted(Comparator.comparing(row -> row.mediaId().getValue())).toList();
        }
    }
}
