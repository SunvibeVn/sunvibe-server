package vn.sunvibe.brokerage.core.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.BrokerageAgreementEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data JPA Repository - brokerage agreements. */
public interface BrokerageAgreementJpaRepository extends JpaRepository<BrokerageAgreementEntity, String> {

    /** The agreement with its versions, in one statement. */
    @Query("select distinct a from BrokerageAgreementEntity a left join fetch a.versions where a.id = :id")
    Optional<BrokerageAgreementEntity> findWithVersions(@Param("id") String id);

    /**
     * The versions of the agreement with their fee rows, in one statement: run after {@link #findWithVersions} in the
     * same persistence context, it fills the fee lists of the versions already loaded (two lists cannot be fetch-joined
     * in one query).
     */
    @Query("select distinct v from AgreementVersionEntity v left join fetch v.fees where v.agreement.id = :id")
    List<AgreementVersionEntity> fetchVersionFees(@Param("id") String agreementId);

    /**
     * The versions of the agreement with their fee tier rows, in one statement: run after {@link #findWithVersions} in
     * the same persistence context, it fills the tier lists of the versions already loaded (E05-S4 spec 5.3).
     */
    @Query("select distinct v from AgreementVersionEntity v left join fetch v.feeTiers where v.agreement.id = :id")
    List<AgreementVersionEntity> fetchVersionFeeTiers(@Param("id") String agreementId);

    /** {@link #findWithVersions} for a set of ids, in one statement; an id without a row is left out. */
    @Query("select distinct a from BrokerageAgreementEntity a left join fetch a.versions where a.id in :ids")
    List<BrokerageAgreementEntity> findWithVersionsIn(@Param("ids") Collection<String> agreementIds);

    /** {@link #fetchVersionFees} for a set of agreements, in one statement. */
    @Query("select distinct v from AgreementVersionEntity v left join fetch v.fees where v.agreement.id in :ids")
    List<AgreementVersionEntity> fetchVersionFeesIn(@Param("ids") Collection<String> agreementIds);

    /** {@link #fetchVersionFeeTiers} for a set of agreements, in one statement. */
    @Query("select distinct v from AgreementVersionEntity v left join fetch v.feeTiers where v.agreement.id in :ids")
    List<AgreementVersionEntity> fetchVersionFeeTiersIn(@Param("ids") Collection<String> agreementIds);

    /**
     * The ids of the owner's agreements that hold at least one version sent to them, newest sending first, then by id
     * (E05-S5 spec S5-D1); the page bounds the count.
     */
    @Query("select a.id from BrokerageAgreementEntity a join a.versions v"
            + " where a.ownerUserId = :owner and v.sentAt is not null"
            + " group by a.id order by max(v.sentAt) desc, a.id asc")
    List<String> findSentToOwnerIds(@Param("owner") String ownerUserId, Pageable page);

    /**
     * The ids of the agreements whose version waits for the owner and was sent at or before the bound, oldest sending
     * first, then by id (E05-S5 spec S5-D8); one version waits per agreement at most, so no id repeats. JPQL, not native
     * SQL, so the lapse job also runs on the H2 test profile; the status literal lets PostgreSQL use the partial index.
     */
    @Query("select v.agreement.id from AgreementVersionEntity v"
            + " where v.status = vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus.AWAITING_OWNER"
            + " and v.sentAt <= :bound order by v.sentAt asc, v.agreement.id asc")
    List<String> findDueLapseIds(@Param("bound") Instant sentAtOrBefore, Pageable page);

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
