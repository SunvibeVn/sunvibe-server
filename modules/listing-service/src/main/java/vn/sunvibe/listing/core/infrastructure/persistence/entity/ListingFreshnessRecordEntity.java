package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - one freshness record of a listing (listing-publish E06-S3 spec 5.3, E06S3-D6). Insert-only: persisted once
 * by the adapter after the listing row's guarded update, then detached.
 */
@Entity
@Table(name = "listing_freshness_records")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingFreshnessRecordEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "listing_id", nullable = false, length = 26)
    private String listingId;

    @Column(name = "listing_row_version", nullable = false)
    private long listingRowVersion;

    @Column(name = "contact_date", nullable = false)
    private LocalDate contactDate;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by", nullable = false, length = 26)
    private String recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private FreshnessChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private FreshnessOutcome outcome;

    @Column(name = "terms_still_apply")
    private Boolean termsStillApply;

    @Column(name = "unit_unchanged")
    private Boolean unitUnchanged;

    @Column(name = "valid", nullable = false)
    private boolean valid;

    /** listing-publish BR-17: private; never in a log, an error or an event. */
    @Column(name = "note", length = 500)
    private String note;

    /** listing-publish BR-17: identifiers only; never the note, the outcome or an answer. */
    @Override
    public String toString() {
        return "ListingFreshnessRecordEntity[id=" + id + ", listingId=" + listingId + ", listingRowVersion="
                + listingRowVersion + "]";
    }
}
