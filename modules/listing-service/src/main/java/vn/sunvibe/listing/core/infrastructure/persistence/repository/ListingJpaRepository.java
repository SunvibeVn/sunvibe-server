package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingEntity;

import java.util.List;

/** Spring Data JPA Repository - the listings' own rows (listing-publish E06-S1 spec 5.3). */
public interface ListingJpaRepository extends JpaRepository<ListingEntity, String> {

    /** The agreement's listings, newest first, then the greater id; one statement on idx_listings_agreement_id. */
    @Query("select l from ListingEntity l where l.agreementId = :agreementId order by l.createdAt desc, l.id desc")
    List<ListingEntity> findNewest(@Param("agreementId") String agreementId, Pageable page);

    boolean existsByAgreementIdAndStateNot(String agreementId, ListingState state);

    boolean existsByCode(String code);
}
