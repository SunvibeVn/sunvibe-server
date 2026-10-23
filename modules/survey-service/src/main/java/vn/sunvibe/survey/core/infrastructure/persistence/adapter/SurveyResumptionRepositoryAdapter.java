package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.resumption.SurveyResumption;
import vn.sunvibe.survey.core.domain.repository.SurveyResumptionDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyResumptionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyResumptionJpaRepository;

import java.util.List;

/**
 * Repository Implementation - SurveyResumptionRepositoryAdapter
 * <p>
 * Implements the domain SurveyResumptionDomainRepository. Resumptions are insert-only, so this adapter offers no way
 * to update or delete one.
 */
@Service
@RequiredArgsConstructor
public class SurveyResumptionRepositoryAdapter implements SurveyResumptionDomainRepository {

    private final SurveyResumptionJpaRepository jpaRepository;

    @Override
    public void save(SurveyResumption resumption) {
        if (resumption == null) {
            throw new IllegalArgumentException("SurveyResumption cannot be null");
        }
        jpaRepository.save(SurveyResumptionEntity.builder()
                .id(resumption.id())
                .leadId(resumption.leadId())
                .visitAppointmentId(resumption.visitAppointmentId().getValue())
                .reason(resumption.reason())
                .basisConfirmedOn(resumption.basis().confirmedOn())
                .basisText(resumption.basis().text())
                .actorUserId(resumption.actorUserId())
                .occurredAt(resumption.occurredAt())
                .build());
    }

    @Override
    public List<SurveyResumption> findByLeadIdOrderByOccurredAt(String leadId) {
        return jpaRepository.findByLeadIdOrderByOccurredAtAscIdAsc(leadId).stream()
                .map(row -> new SurveyResumption(row.getId(), row.getLeadId(),
                        SurveyAppointmentId.of(row.getVisitAppointmentId()), row.getReason(),
                        new Basis(row.getBasisConfirmedOn(), row.getBasisText()), row.getActorUserId(),
                        row.getOccurredAt()))
                .toList();
    }
}
