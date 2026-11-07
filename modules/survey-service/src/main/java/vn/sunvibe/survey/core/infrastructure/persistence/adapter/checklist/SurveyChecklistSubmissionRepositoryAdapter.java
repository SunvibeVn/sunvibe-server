package vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSubmissionDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistSubmissionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.json.ChecklistDocumentCodec;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist.SurveyChecklistSubmissionJpaRepository;

import java.util.Optional;

/** Adapter - the insert-only submission rows (survey-checklist submission spec 4.3, I-2). */
@Service
@RequiredArgsConstructor
public class SurveyChecklistSubmissionRepositoryAdapter implements SurveyChecklistSubmissionDomainRepository {

    private final SurveyChecklistSubmissionJpaRepository jpaRepository;
    private final ChecklistDocumentCodec codec;

    @Override
    public void save(ChecklistSubmission submission) {
        jpaRepository.saveAndFlush(SurveyChecklistSubmissionEntity.builder()
                .checklistId(submission.checklistId().getValue())
                .submittedByUserId(submission.submittedByUserId())
                .submittedAt(submission.submittedAt())
                .templateCode(submission.templateCode())
                .templateVersion(submission.templateVersion())
                .kind(submission.kind())
                .leadIntent(submission.leadIntent())
                .intentCycle(submission.intentCycle())
                .visitAppointmentId(submission.visitAppointmentId().getValue())
                .visitPerformedBy(submission.visitPerformedBy())
                .visitArrivedAt(submission.visitArrivedAt())
                .visitRecordedBy(submission.visitRecordedBy())
                .visitRecordedAt(submission.visitRecordedAt())
                .rentalScope(submission.rentalScope())
                .propertyType(submission.propertyTypeOrNull())
                .spaces(codec.writeSubmittedSpaces(submission.spaces()))
                .groupVersions(codec.writeGroupVersions(submission.groupVersions()))
                .mediaPolicy(submission.mediaPolicy())
                .contentSha256(submission.contentSha256())
                .build());
    }

    @Override
    public Optional<ChecklistSubmission> findByChecklistId(SurveyChecklistId checklistId) {
        return jpaRepository.findById(checklistId.getValue()).map(row -> new ChecklistSubmission(
                SurveyChecklistId.of(row.getChecklistId()), row.getSubmittedByUserId(), row.getSubmittedAt(),
                row.getTemplateCode(), row.getTemplateVersion(), row.getKind(), row.getLeadIntent(),
                row.getIntentCycle(), SurveyAppointmentId.of(row.getVisitAppointmentId()), row.getVisitPerformedBy(),
                row.getVisitArrivedAt(), row.getVisitRecordedBy(), row.getVisitRecordedAt(), row.getRentalScope(),
                row.getPropertyType(), codec.readSubmittedSpaces(row.getSpaces()),
                codec.readGroupVersions(row.getGroupVersions()), row.getMediaPolicy(), row.getContentSha256()));
    }
}
