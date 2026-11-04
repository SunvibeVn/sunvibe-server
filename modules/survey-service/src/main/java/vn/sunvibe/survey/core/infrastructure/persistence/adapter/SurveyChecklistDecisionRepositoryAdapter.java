package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDecisionDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistDecisionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistEvidenceRejectionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistMediaKey;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyChecklistDecisionJpaRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyChecklistEvidenceRejectionJpaRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adapter - the insert-only decisions and their photo marks (survey-checklist submission spec 4.5, I-2). */
@Service
@RequiredArgsConstructor
public class SurveyChecklistDecisionRepositoryAdapter implements SurveyChecklistDecisionDomainRepository {

    private final SurveyChecklistDecisionJpaRepository decisions;
    private final SurveyChecklistEvidenceRejectionJpaRepository rejections;

    @Override
    public void save(ChecklistDecision decision) {
        String checklistId = decision.checklistId().getValue();
        decisions.saveAndFlush(SurveyChecklistDecisionEntity.builder()
                .checklistId(checklistId)
                .outcome(decision.outcome())
                .needsChangesKind(decision.needsChangesKindOrNull())
                .failedKind(decision.failedKindOrNull())
                .reason(decision.reasonOrNull())
                .notes(decision.notesOrNull())
                .limitations(decision.limitationsOrNull())
                .reviewerUserId(decision.reviewerUserId())
                .decidedAt(decision.decidedAt())
                .leadStatusBefore(decision.leadStatusBefore())
                .leadStatusAfter(decision.leadStatusAfter())
                .build());
        rejections.saveAllAndFlush(decision.rejections().stream()
                .map(rejection -> SurveyChecklistEvidenceRejectionEntity.builder()
                        .id(new SurveyChecklistMediaKey(checklistId, rejection.mediaId()))
                        .reason(rejection.reason())
                        .build())
                .toList());
    }

    @Override
    public Optional<ChecklistDecision> findByChecklistId(SurveyChecklistId checklistId) {
        return Optional.ofNullable(findByChecklistIds(List.of(checklistId)).get(checklistId));
    }

    @Override
    public Map<SurveyChecklistId, ChecklistDecision> findByChecklistIds(Collection<SurveyChecklistId> checklistIds) {
        if (checklistIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = checklistIds.stream().map(SurveyChecklistId::getValue).toList();
        Map<String, List<EvidenceRejection>> marks = new HashMap<>();
        for (SurveyChecklistEvidenceRejectionEntity row : rejections.findByIdChecklistIdInOrderByIdMediaIdAsc(ids)) {
            marks.computeIfAbsent(row.getId().getChecklistId(), id -> new ArrayList<>())
                    .add(new EvidenceRejection(row.getId().getMediaId(), row.getReason()));
        }
        Map<SurveyChecklistId, ChecklistDecision> found = new LinkedHashMap<>();
        for (SurveyChecklistDecisionEntity row : decisions.findAllById(ids)) {
            SurveyChecklistId id = SurveyChecklistId.of(row.getChecklistId());
            found.put(id, new ChecklistDecision(id, row.getOutcome(), row.getNeedsChangesKind(), row.getFailedKind(),
                    row.getReason(), row.getNotes(), row.getLimitations(), row.getReviewerUserId(),
                    row.getDecidedAt(), row.getLeadStatusBefore(), row.getLeadStatusAfter(),
                    marks.getOrDefault(row.getChecklistId(), List.of())));
        }
        return found;
    }
}
