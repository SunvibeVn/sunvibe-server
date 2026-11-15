package vn.sunvibe.brokerage.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.BrokerageAgreementEntity;

import java.util.List;

/** Mapper - a brokerage agreement and its versions, both ways, every child mapped. */
@Component
public class AgreementEntityMapper {

    /**
     * The entity of the aggregate with every version. A new aggregate has no row version, so Spring Data persists it;
     * a loaded one keeps the version it was read with.
     */
    public BrokerageAgreementEntity toEntity(BrokerageAgreement agreement) {
        BrokerageAgreementEntity entity = BrokerageAgreementEntity.builder()
                .id(agreement.getId())
                .unitId(agreement.getUnitId())
                .unitCode(agreement.getUnitCodeOrNull())
                .propertyId(agreement.getPropertyId())
                .transactionType(agreement.getTransactionType())
                .ownerUserId(agreement.getOwnerUserId())
                .basisLeadId(agreement.getBasisLeadId())
                .status(agreement.getStatus())
                .createdByUserId(agreement.getCreatedByUserId())
                .createdAt(agreement.getCreatedAt())
                .updatedAt(agreement.getUpdatedAt())
                .version(agreement.getRowVersionOrNull())
                .build();
        for (AgreementVersion version : agreement.getVersions()) {
            entity.getVersions().add(AgreementVersionEntity.builder()
                    .id(version.getId())
                    .agreement(entity)
                    .versionNo(version.getVersionNo())
                    .status(version.getStatus())
                    .startDate(version.getPeriod().startDate())
                    .endDate(version.getPeriod().endDate())
                    .basisLeadId(version.getBasisLeadId())
                    .basisIntakeId(version.getBasisIntakeId())
                    .createdByUserId(version.getCreatedByUserId())
                    .createdAt(version.getCreatedAt())
                    .updatedAt(version.getUpdatedAt())
                    .build());
        }
        return entity;
    }

    /** The aggregate of a stored agreement, its versions by number. */
    public BrokerageAgreement toDomain(BrokerageAgreementEntity entity) {
        List<AgreementVersion> versions = entity.getVersions().stream()
                .map(version -> AgreementVersion.load(version.getId(), version.getVersionNo(), version.getStatus(),
                        new AgreementPeriod(version.getStartDate(), version.getEndDate()), version.getBasisLeadId(),
                        version.getBasisIntakeId(), version.getCreatedByUserId(), version.getCreatedAt(),
                        version.getUpdatedAt()))
                .toList();
        return BrokerageAgreement.load(entity.getId(), entity.getUnitId(), entity.getUnitCode(),
                entity.getPropertyId(), entity.getTransactionType(), entity.getOwnerUserId(), entity.getBasisLeadId(),
                entity.getStatus(), entity.getCreatedByUserId(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion(), versions);
    }
}
