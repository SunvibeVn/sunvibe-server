package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSelectionDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistMediaKey;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaSelectionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyMediaSelectionJpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adapter - photos carried into later revisions (survey-checklist submission spec 4.6, 5.3, A-12). */
@Service
@RequiredArgsConstructor
public class SurveyMediaSelectionRepositoryAdapter implements SurveyMediaSelectionDomainRepository {

    private final SurveyMediaSelectionJpaRepository jpaRepository;

    @Override
    public void insertAll(List<MediaSelection> selections) {
        jpaRepository.saveAllAndFlush(selections.stream().map(SurveyMediaSelectionRepositoryAdapter::toEntity)
                .toList());
    }

    @Override
    public void update(MediaSelection selection) {
        jpaRepository.saveAndFlush(toEntity(selection));
    }

    @Override
    public List<MediaSelection> findByChecklistId(String checklistId) {
        return findByChecklistIds(List.of(checklistId));
    }

    @Override
    public Optional<MediaSelection> findOne(String checklistId, SurveyMediaId mediaId) {
        return jpaRepository.findById(new SurveyChecklistMediaKey(checklistId, mediaId.getValue()))
                .map(SurveyMediaSelectionRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<String> findCarriedFrom(String checklistId, SurveyMediaId mediaId) {
        return jpaRepository.findCarriedFrom(checklistId, mediaId.getValue());
    }

    @Override
    public List<String> findSelectingChecklistIds(SurveyMediaId mediaId) {
        return jpaRepository.findSelectingChecklistIds(mediaId.getValue());
    }

    @Override
    public List<MediaSelection> findByChecklistIds(Collection<String> checklistIds) {
        if (checklistIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByIdChecklistIdInOrderByIdMediaIdAsc(checklistIds).stream()
                .map(SurveyMediaSelectionRepositoryAdapter::toDomain).toList();
    }

    private static SurveyMediaSelectionEntity toEntity(MediaSelection selection) {
        MediaRemovalCorrection correction = selection.correctionOrNull();
        return SurveyMediaSelectionEntity.builder()
                .id(new SurveyChecklistMediaKey(selection.checklistId(), selection.mediaId().getValue()))
                .carriedFromChecklistId(selection.carriedFromChecklistId())
                .selectedAt(selection.selectedAt())
                .deselectedAt(selection.deselectedAtOrNull())
                .deselectedByUserId(selection.deselectedByUserIdOrNull())
                .deselectionKind(selection.deselectionKindOrNull())
                .deselectedBySaveId(selection.deselectedBySaveIdOrNull())
                .correctionReason(correction == null ? null : correction.reason())
                .correctionBasisOn(correction == null ? null : correction.basis().confirmedOn())
                .correctionBasisText(correction == null ? null : correction.basis().text())
                .build();
    }

    private static MediaSelection toDomain(SurveyMediaSelectionEntity row) {
        return new MediaSelection(row.getId().getChecklistId(), SurveyMediaId.of(row.getId().getMediaId()),
                row.getCarriedFromChecklistId(), row.getSelectedAt(), row.getDeselectedAt(),
                row.getDeselectedByUserId(), row.getDeselectionKind(), row.getDeselectedBySaveId(),
                row.getCorrectionReason() == null ? null : new MediaRemovalCorrection(row.getCorrectionReason(),
                        new Basis(row.getCorrectionBasisOn(), row.getCorrectionBasisText())));
    }
}
