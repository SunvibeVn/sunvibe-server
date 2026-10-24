package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.exception.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyChecklistJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter - checklist rows on JPA. Both unique indexes of a checklist (one number per lead, one draft per visit,
 * survey-checklist spec I-2) become {@link ChecklistAlreadyStartedException}.
 */
@Service
@RequiredArgsConstructor
public class SurveyChecklistRepositoryAdapter implements SurveyChecklistDomainRepository {

    static final Set<String> STARTED_INDEXES = Set.of("uq_survey_checklists_lead_no",
            "uq_survey_checklists_draft_per_appointment");

    private final SurveyChecklistJpaRepository jpaRepository;

    @Override
    public void insert(SurveyChecklist checklist) {
        if (checklist.getVersion() != null) {
            throw new IllegalArgumentException("Only a new checklist is inserted.");
        }
        try {
            checklist.markPersisted(jpaRepository.saveAndFlush(toEntity(checklist)).getVersion());
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.violatesAny(e, STARTED_INDEXES)) {
                throw new ChecklistAlreadyStartedException(e);
            }
            throw e;
        }
    }

    @Override
    public void update(SurveyChecklist checklist) {
        if (checklist.getVersion() == null) {
            throw new IllegalArgumentException("A new checklist is inserted, not updated.");
        }
        checklist.markPersisted(jpaRepository.saveAndFlush(toEntity(checklist)).getVersion());
    }

    @Override
    public Optional<SurveyChecklist> findById(SurveyChecklistId id) {
        return jpaRepository.findById(id.getValue()).map(SurveyChecklistRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<String> findLeadIdById(SurveyChecklistId id) {
        return jpaRepository.findLeadIdById(id.getValue());
    }

    @Override
    public List<SurveyChecklist> findByLeadId(String leadId) {
        return jpaRepository.findByLeadIdOrderByChecklistNoAsc(leadId).stream()
                .map(SurveyChecklistRepositoryAdapter::toDomain).toList();
    }

    @Override
    public int nextChecklistNo(String leadId) {
        return jpaRepository.nextChecklistNo(leadId);
    }

    private static SurveyChecklistEntity toEntity(SurveyChecklist checklist) {
        return SurveyChecklistEntity.builder()
                .id(checklist.getId().getValue())
                .leadId(checklist.getLeadId())
                .checklistNo(checklist.getChecklistNo())
                .visitAppointmentId(checklist.getVisitAppointmentId().getValue())
                .intentCycle(checklist.getIntentCycle())
                .holderUserId(checklist.getHolderUserId())
                .templateCode(checklist.getTemplateCode())
                .templateVersion(checklist.getTemplateVersion())
                .kind(checklist.getKind())
                .status(checklist.getStatus())
                .createdBy(checklist.getCreatedBy())
                .createdAt(checklist.getCreatedAt())
                .version(checklist.getVersion())
                .build();
    }

    private static SurveyChecklist toDomain(SurveyChecklistEntity entity) {
        return SurveyChecklist.load(SurveyChecklistId.of(entity.getId()), entity.getLeadId(), entity.getChecklistNo(),
                SurveyAppointmentId.of(entity.getVisitAppointmentId()), entity.getIntentCycle(),
                entity.getHolderUserId(), entity.getTemplateCode(), entity.getTemplateVersion(), entity.getKind(),
                entity.getStatus(), entity.getCreatedBy(), entity.getCreatedAt(), entity.getVersion());
    }
}
