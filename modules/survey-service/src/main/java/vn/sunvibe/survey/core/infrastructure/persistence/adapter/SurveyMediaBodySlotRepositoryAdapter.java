package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaBodySlotDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaBodySlotEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyMediaBodySlotJpaRepository;

import java.time.Instant;

/** Adapter - content body slots of users across instances (survey-media spec A-3, 5.2). */
@Service
@RequiredArgsConstructor
public class SurveyMediaBodySlotRepositoryAdapter implements SurveyMediaBodySlotDomainRepository {

    private final SurveyMediaBodySlotJpaRepository jpaRepository;

    @Override
    public long take(String userId, String instanceId, Instant now, Instant expiresAt) {
        return jpaRepository.saveAndFlush(SurveyMediaBodySlotEntity.builder()
                .userId(userId)
                .instanceId(instanceId)
                .takenAt(now)
                .expiresAt(expiresAt)
                .build()).getId();
    }

    @Override
    public void free(long slotId) {
        jpaRepository.deleteById(slotId);
        jpaRepository.flush();
    }

    @Override
    public int countLive(String userId, Instant now) {
        return (int) jpaRepository.countLive(userId, now);
    }

    @Override
    public int deleteExpired(Instant now) {
        return jpaRepository.deleteExpired(now);
    }
}
