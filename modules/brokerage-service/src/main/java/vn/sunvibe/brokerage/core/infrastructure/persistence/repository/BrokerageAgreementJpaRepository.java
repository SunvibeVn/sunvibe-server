package vn.sunvibe.brokerage.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.BrokerageAgreementEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data JPA Repository - brokerage agreements. */
public interface BrokerageAgreementJpaRepository extends JpaRepository<BrokerageAgreementEntity, String> {

    /** The agreement with its versions, in one statement. */
    @Query("select distinct a from BrokerageAgreementEntity a left join fetch a.versions where a.id = :id")
    Optional<BrokerageAgreementEntity> findWithVersions(@Param("id") String id);

    /** The agreements of those units and that type in those statuses, as ids only. */
    @Query("select a.id as agreementId, a.unitId as unitId, a.basisLeadId as basisLeadId"
            + " from BrokerageAgreementEntity a"
            + " where a.unitId in :unitIds and a.transactionType = :type and a.status in :statuses")
    List<UnfinishedRow> findUnfinished(@Param("unitIds") Collection<String> unitIds,
                                       @Param("type") TransactionType type,
                                       @Param("statuses") Collection<AgreementStatus> statuses);

    /** One row of {@link #findUnfinished}. */
    interface UnfinishedRow {
        String getAgreementId();

        String getUnitId();

        String getBasisLeadId();
    }
}
