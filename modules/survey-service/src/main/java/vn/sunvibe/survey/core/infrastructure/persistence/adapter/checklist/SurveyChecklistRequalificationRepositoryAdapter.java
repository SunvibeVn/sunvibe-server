package vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistRequalificationDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistRequalificationEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist.SurveyChecklistRequalificationJpaRepository;

import java.util.Optional;

/** Adapter - the insert-only reason of a requalification mark (property-create requalification spec 5.2, I-2). */
@Service
@RequiredArgsConstructor
public class SurveyChecklistRequalificationRepositoryAdapter implements SurveyChecklistRequalificationDomainRepository {

    private final SurveyChecklistRequalificationJpaRepository requalifications;

    @Override
    public void save(ChecklistRequalification requalification) {
        requalifications.saveAndFlush(SurveyChecklistRequalificationEntity.builder()
                .checklistId(requalification.checklistId().getValue())
                .reasonKind(requalification.kind())
                .reason(requalification.reason())
                .eventSource(requalification.eventSource())
                .scope(requalification.scope())
                .basisDate(requalification.basisDate())
                .markedByUserId(requalification.markedByUserId())
                .markedAt(requalification.markedAt())
                .leadStatusBefore(requalification.leadStatusBefore())
                .leadStatusAfter(requalification.leadStatusAfter())
                .build());
    }

    @Override
    public Optional<ChecklistRequalification> findByChecklistId(SurveyChecklistId checklistId) {
        return requalifications.findById(checklistId.getValue()).map(row -> new ChecklistRequalification(checklistId,
                row.getReasonKind(), row.getReason(), row.getEventSource(), row.getScope(), row.getBasisDate(),
                row.getMarkedByUserId(), row.getMarkedAt(), row.getLeadStatusBefore(), row.getLeadStatusAfter()));
    }
}
