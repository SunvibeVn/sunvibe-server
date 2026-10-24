package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAccessLogEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyAccessLogJpaRepository;

import java.util.List;

/**
 * Repository Implementation - SurveyAccessLogRepositoryAdapter
 * <p>
 * Implements the domain SurveyAccessLogDomainRepository. The log is insert-only, so this adapter offers no way to
 * update or delete an entry.
 */
@Service
@RequiredArgsConstructor
public class SurveyAccessLogRepositoryAdapter implements SurveyAccessLogDomainRepository {

    private final SurveyAccessLogJpaRepository jpaRepository;

    @Override
    public void save(SurveyAccessLog log) {
        if (log == null) {
            throw new IllegalArgumentException("SurveyAccessLog cannot be null");
        }
        jpaRepository.save(SurveyAccessLogEntity.builder()
                .id(log.id())
                .leadId(log.leadId())
                .userId(log.userId())
                .accessedAt(log.accessedAt())
                .resourceType(log.resourceTypeOrNull())
                .resourceId(log.resourceIdOrNull())
                .build());
    }

    @Override
    public List<SurveyAccessLog> findByLeadId(String leadId) {
        return jpaRepository.findByLeadIdOrderByAccessedAtAscIdAsc(leadId).stream()
                .map(row -> new SurveyAccessLog(row.getId(), row.getLeadId(), row.getUserId(), row.getAccessedAt(),
                        row.getResourceType(), row.getResourceId()))
                .toList();
    }
}
