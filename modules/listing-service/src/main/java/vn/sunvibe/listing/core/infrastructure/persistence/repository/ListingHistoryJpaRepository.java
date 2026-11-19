package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingHistoryEntity;

/** Spring Data JPA Repository - the history rows of the listings (listing-publish E06-S1 spec 5.3). Insert-only. */
public interface ListingHistoryJpaRepository extends JpaRepository<ListingHistoryEntity, String> {
}
