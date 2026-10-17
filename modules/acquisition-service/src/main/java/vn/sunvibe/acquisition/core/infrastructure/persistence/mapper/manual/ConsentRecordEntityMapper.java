package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.ConsentRecordEntity;

/**
 * Mapper to convert between the ConsentRecord Domain Model and ConsentRecordEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database (rebuilt with ConsentRecord.load).
 */
@Component
public class ConsentRecordEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public ConsentRecord toDomain(ConsentRecordEntity entity) {
        if (entity == null) return null;

        return ConsentRecord.load(
                ConsentRecordId.of(entity.getId()),
                entity.getNoticeVersion(),
                entity.getPurpose(),
                entity.getChannel(),
                entity.getIpHash(),
                entity.getUserAgentHash(),
                entity.getAcceptedAt()
        );
    }

    /**
     * Convert Domain → Entity.
     */
    public ConsentRecordEntity toEntity(ConsentRecord domain) {
        if (domain == null) return null;

        return ConsentRecordEntity.builder()
                .id(domain.getId().getValue())
                .noticeVersion(domain.getNoticeVersion())
                .purpose(domain.getPurpose())
                .channel(domain.getChannel())
                .ipHash(domain.getIpHash())
                .userAgentHash(domain.getUserAgentHash())
                .acceptedAt(domain.getAcceptedAt())
                .build();
    }
}
