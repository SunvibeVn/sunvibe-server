package vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.Correction;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSaveDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistSaveEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.json.ChecklistDocumentCodec;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist.SurveyChecklistSaveJpaRepository;

import java.util.Collection;
import java.util.List;

/** Adapter - the insert-only save log of checklists (survey-checklist spec 5.2, I-6). */
@Service
@RequiredArgsConstructor
public class SurveyChecklistSaveRepositoryAdapter implements SurveyChecklistSaveDomainRepository {

    private final SurveyChecklistSaveJpaRepository jpaRepository;
    private final ChecklistDocumentCodec codec;

    @Override
    public void save(ChecklistSave save) {
        Correction correction = save.correctionOrNull();
        jpaRepository.save(SurveyChecklistSaveEntity.builder()
                .id(save.id())
                .checklistId(save.checklistId().getValue())
                .groupKey(save.group())
                .groupVersion(save.groupVersion())
                .actorUserId(save.actorUserId())
                .savedAt(save.savedAt())
                .correctionReason(correction == null ? null : correction.reason())
                .correctionBasisConfirmedOn(correction == null ? null : correction.basis().confirmedOn())
                .correctionBasisText(correction == null ? null : correction.basis().text())
                .correctedUnits(correction == null ? null : codec.writeCorrectedUnits(correction.units()))
                .build());
    }

    @Override
    public List<ChecklistPerson> findActors(Collection<SurveyChecklistId> checklistIds) {
        return checklistIds.isEmpty() ? List.of()
                : jpaRepository.findActors(checklistIds.stream().map(SurveyChecklistId::getValue).toList());
    }

    @Override
    public List<ChecklistSave> findByChecklistId(SurveyChecklistId checklistId) {
        return jpaRepository.findByChecklistIdOrderBySavedAtAscIdAsc(checklistId.getValue()).stream()
                .map(row -> new ChecklistSave(row.getId(), SurveyChecklistId.of(row.getChecklistId()),
                        row.getGroupKey(), row.getGroupVersion(), row.getActorUserId(), row.getSavedAt(),
                        row.getCorrectionReason() == null ? null : new Correction(row.getCorrectionReason(),
                                new Basis(row.getCorrectionBasisConfirmedOn(), row.getCorrectionBasisText()),
                                codec.readCorrectedUnits(row.getCorrectedUnits()))))
                .toList();
    }
}
