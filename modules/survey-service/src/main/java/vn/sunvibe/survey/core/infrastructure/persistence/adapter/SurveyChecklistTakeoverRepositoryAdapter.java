package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSnapshot;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistTakeoverEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.json.ChecklistDocumentCodec;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyChecklistTakeoverJpaRepository;

import java.util.List;
import java.util.Optional;

/** Adapter - insert-only takeover rows with their checkpoints (survey-checklist spec 5.2, I-6). */
@Service
@RequiredArgsConstructor
public class SurveyChecklistTakeoverRepositoryAdapter implements SurveyChecklistTakeoverDomainRepository {

    private final SurveyChecklistTakeoverJpaRepository jpaRepository;
    private final ChecklistDocumentCodec codec;

    @Override
    public void save(ChecklistTakeover takeover) {
        jpaRepository.saveAndFlush(SurveyChecklistTakeoverEntity.builder()
                .id(takeover.id())
                .checklistId(takeover.checklistId().getValue())
                .takeoverNo(takeover.takeoverNo())
                .kind(takeover.kind())
                .fromUserId(takeover.fromUserId())
                .toUserId(takeover.toUserId())
                .reason(takeover.reason())
                .basisConfirmedOn(takeover.basis().confirmedOn())
                .basisText(takeover.basis().text())
                .snapshot(takeover.snapshotOrNull() == null ? null
                        : codec.writeSnapshot(takeover.snapshotOrNull().groups()))
                .occurredAt(takeover.occurredAt())
                .build());
    }

    @Override
    public List<ChecklistTakeover> findByChecklistId(SurveyChecklistId checklistId) {
        return jpaRepository.findByChecklistIdOrderByTakeoverNoAsc(checklistId.getValue()).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public Optional<ChecklistTakeover> find(SurveyChecklistId checklistId, int takeoverNo) {
        return jpaRepository.findByChecklistIdAndTakeoverNo(checklistId.getValue(), takeoverNo).map(this::toDomain);
    }

    @Override
    public int findNextTakeoverNo(SurveyChecklistId checklistId) {
        return jpaRepository.nextTakeoverNo(checklistId.getValue());
    }

    private ChecklistTakeover toDomain(SurveyChecklistTakeoverEntity row) {
        return new ChecklistTakeover(row.getId(), SurveyChecklistId.of(row.getChecklistId()), row.getTakeoverNo(),
                row.getKind(), row.getFromUserId(), row.getToUserId(), row.getReason(),
                new Basis(row.getBasisConfirmedOn(), row.getBasisText()),
                row.getSnapshot() == null ? null : new ChecklistSnapshot(codec.readSnapshot(row.getSnapshot())),
                row.getOccurredAt());
    }
}
