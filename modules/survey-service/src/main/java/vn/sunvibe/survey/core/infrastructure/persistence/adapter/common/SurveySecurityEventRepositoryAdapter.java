package vn.sunvibe.survey.core.infrastructure.persistence.adapter.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.repository.common.SurveySecurityEventDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.common.SurveySecurityEventEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.common.SurveySecurityEventJpaRepository;

import java.util.List;

/** Adapter - insert-only security events (survey-checklist spec 4.10, I-6). */
@Service
@RequiredArgsConstructor
public class SurveySecurityEventRepositoryAdapter implements SurveySecurityEventDomainRepository {

    private final SurveySecurityEventJpaRepository jpaRepository;

    @Override
    public void save(SurveySecurityEvent event) {
        jpaRepository.save(SurveySecurityEventEntity.builder()
                .id(event.id())
                .userId(event.userId())
                .leadId(event.leadId())
                .resourceType(event.resourceType())
                .resourceId(event.resourceId())
                .action(event.action())
                .outcome(event.outcome())
                .correlationId(event.correlationIdOrNull())
                .occurredAt(event.occurredAt())
                .requestId(event.requestIdOrNull())
                .build());
    }

    @Override
    public List<SurveySecurityEvent> findByLeadId(String leadId) {
        return jpaRepository.findByLeadIdOrderByOccurredAtAscIdAsc(leadId).stream()
                .map(row -> new SurveySecurityEvent(row.getId(), row.getUserId(), row.getLeadId(),
                        row.getResourceType(), row.getResourceId(), row.getAction(), row.getOutcome(),
                        row.getCorrelationId(), row.getOccurredAt(), row.getRequestId()))
                .toList();
    }
}
