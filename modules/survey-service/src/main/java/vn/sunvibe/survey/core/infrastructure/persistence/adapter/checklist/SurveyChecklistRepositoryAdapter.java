package vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistSubmittedConflictException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.repository.common.PageResult;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.support.ConstraintViolations;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist.SurveyChecklistJpaRepository;

import java.util.Collection;
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

    /**
     * submission spec I-1: one SUBMITTED revision per lead and intent cycle, also under concurrent submits;
     * property-create requalification spec I-1: one unmarked PASSED per lead and intent cycle, also under concurrent
     * decisions.
     */
    static final Set<String> CYCLE_INDEXES = Set.of("uq_survey_checklists_submitted_per_cycle",
            "uq_survey_checklists_current_passed_per_cycle");

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
        try {
            checklist.markPersisted(jpaRepository.saveAndFlush(toEntity(checklist)).getVersion());
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.violatesAny(e, CYCLE_INDEXES)) {
                throw new ChecklistSubmittedConflictException(e);
            }
            throw e;
        }
    }

    @Override
    public Optional<SurveyChecklist> findById(SurveyChecklistId id) {
        return jpaRepository.findById(id.getValue()).map(SurveyChecklistRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<SurveyChecklist> findByIdForShare(SurveyChecklistId id) {
        return jpaRepository.findForShareById(id.getValue()).map(SurveyChecklistRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<SurveyChecklist> findByIdForUpdate(SurveyChecklistId id) {
        return jpaRepository.findForNoKeyUpdateById(id.getValue()).map(SurveyChecklistRepositoryAdapter::toDomain);
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
    public List<SurveyChecklist> findByVisitAppointmentIds(Collection<SurveyAppointmentId> visitIds) {
        if (visitIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByVisitAppointmentIdInOrderByChecklistNoAsc(visitIds.stream()
                .map(SurveyAppointmentId::getValue).toList()).stream()
                .map(SurveyChecklistRepositoryAdapter::toDomain).toList();
    }

    @Override
    public PageResult<SurveyChecklist> findSubmittedOf(List<String> leadIds, List<Integer> intentCycles, int page,
                                                       int size) {
        String[] leads = leadIds.toArray(String[]::new);
        Integer[] cycles = intentCycles.toArray(Integer[]::new);
        return new PageResult<>(jpaRepository.findSubmittedPage(leads, cycles, size, (long) page * size).stream()
                .map(SurveyChecklistRepositoryAdapter::toDomain).toList(), page, size,
                jpaRepository.countSubmitted(leads, cycles));
    }

    @Override
    public int nextChecklistNo(String leadId) {
        return jpaRepository.nextChecklistNo(leadId);
    }

    static SurveyChecklistEntity toEntity(SurveyChecklist checklist) {
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
                .previousChecklistId(checklist.getPreviousChecklistIdOrNull() == null ? null
                        : checklist.getPreviousChecklistIdOrNull().getValue())
                .submittedAt(checklist.getSubmittedAt())
                .submittedByUserId(checklist.getSubmittedByUserId())
                .decidedAt(checklist.getDecidedAt())
                .requalifiedAt(checklist.getRequalifiedAt())
                .version(checklist.getVersion())
                .build();
    }

    static SurveyChecklist toDomain(SurveyChecklistEntity entity) {
        return SurveyChecklist.load(SurveyChecklistId.of(entity.getId()), entity.getLeadId(), entity.getChecklistNo(),
                SurveyAppointmentId.of(entity.getVisitAppointmentId()), entity.getIntentCycle(),
                entity.getHolderUserId(), entity.getTemplateCode(), entity.getTemplateVersion(), entity.getKind(),
                entity.getStatus(), entity.getCreatedBy(), entity.getCreatedAt(),
                entity.getPreviousChecklistId() == null ? null : SurveyChecklistId.of(entity.getPreviousChecklistId()),
                entity.getSubmittedAt(), entity.getSubmittedByUserId(), entity.getDecidedAt(),
                entity.getRequalifiedAt(), entity.getVersion());
    }
}
