package vn.sunvibe.listing.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingHistoryEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionEntity;

import java.util.List;

/**
 * Manual Mapper - a listing to its three tables' entities, and rows back to domain values through the domain's own
 * factories ({@link ListingRevision#load}, {@link ListingCode#of}); no reflection.
 */
@Component
public class ListingEntityMapper {

    /** The listing's own row of a new listing: a null row version, so the insert is a persist (E06S1-P13). */
    public ListingEntity toNewEntity(Listing listing) {
        return ListingEntity.builder()
                .id(listing.id())
                .code(listing.code().value())
                .unitId(listing.unitId())
                .transactionType(listing.transactionType())
                .agreementId(listing.agreementId())
                .state(listing.state())
                .createdBy(listing.createdBy())
                .createdAt(listing.createdAt())
                .updatedAt(listing.updatedAt())
                .rowVersion(null)
                .build();
    }

    public List<ListingRevisionEntity> toRevisionEntities(Listing listing) {
        return listing.revisions().stream()
                .map(revision -> ListingRevisionEntity.builder()
                        .id(revision.id())
                        .listingId(listing.id())
                        .revisionNo(revision.revisionNo())
                        .status(revision.status())
                        .createdBy(revision.createdBy())
                        .createdAt(revision.createdAt())
                        .updatedAt(revision.updatedAt())
                        .build())
                .toList();
    }

    public List<ListingHistoryEntity> toHistoryEntities(Listing listing) {
        return listing.pendingHistory().stream().map(entry -> toHistoryEntity(listing.id(), entry)).toList();
    }

    public ListingRow toRow(ListingEntity entity) {
        return new ListingRow(entity.getId(), ListingCode.of(entity.getCode()), entity.getUnitId(),
                entity.getTransactionType(), entity.getAgreementId(), entity.getState(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getRowVersion() == null ? 0L : entity.getRowVersion());
    }

    public ListingRevision toRevision(ListingRevisionEntity entity) {
        return ListingRevision.load(entity.getId(), entity.getRevisionNo(), entity.getStatus(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static ListingHistoryEntity toHistoryEntity(String listingId, ListingHistoryEntry entry) {
        return ListingHistoryEntity.builder()
                .id(entry.id())
                .listingId(listingId)
                .action(entry.action())
                .revisionNo(entry.revisionNoOrNull())
                .actorUserId(entry.actorIdOrNull())
                .occurredAt(entry.occurredAt())
                .build();
    }
}
