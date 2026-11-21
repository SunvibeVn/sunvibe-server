package vn.sunvibe.listing.core.domain.model.listing;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One contact of the assigned Sale with the person letting the unit (listing-publish US-6 AC-1, BR-9, BR-11; E06-S3 spec
 * E06S3-D4, E06S3-D6, E06S3-D10). The two answers of BR-9 are present exactly when the outcome is
 * {@link FreshnessOutcome#AVAILABLE}; the record is a valid confirmation only then, with both answers yes. The contact
 * date is the server's Vietnam date; {@code listingRowVersion} is the listing's row version the record moved it to,
 * unique per listing, so the latest record is the one with the greatest value.
 *
 * @param noteOrNull the private note in its stored form (BR-17), if any
 */
public record FreshnessRecord(String id, String listingId, long listingRowVersion, LocalDate contactDate,
                              Instant recordedAt, String recordedBy, FreshnessChannel channel,
                              FreshnessOutcome outcome, Boolean termsStillApplyOrNull, Boolean unitUnchangedOrNull,
                              String noteOrNull) {

    public FreshnessRecord {
        if (id == null || listingId == null || contactDate == null || recordedAt == null || recordedBy == null
                || channel == null || outcome == null) {
            throw new IllegalArgumentException("a freshness record needs its ids, date, time, recorder and codes");
        }
        if (listingRowVersion < 1) {
            throw new IllegalArgumentException("a freshness record moves the listing to a row version of at least 1");
        }
        boolean answered = termsStillApplyOrNull != null && unitUnchangedOrNull != null;
        boolean unanswered = termsStillApplyOrNull == null && unitUnchangedOrNull == null;
        // listing-publish BR-9: the two questions are asked exactly when the unit is still available
        if (outcome == FreshnessOutcome.AVAILABLE ? !answered : !unanswered) {
            throw new IllegalArgumentException("a freshness record's answers do not match its outcome");
        }
        if (noteOrNull != null && (!noteOrNull.equals(FreshnessNote.normalized(noteOrNull))
                || !FreshnessNote.storable(noteOrNull))) {
            throw new IllegalArgumentException("a freshness record's note is stored only in its checked form");
        }
    }

    /** listing-publish BR-9, BR-11: a valid confirmation is "still available" with both answers yes. */
    public boolean valid() {
        return outcome == FreshnessOutcome.AVAILABLE && Boolean.TRUE.equals(termsStillApplyOrNull)
                && Boolean.TRUE.equals(unitUnchangedOrNull);
    }

    /** listing-publish BR-17: identifiers only; never the note, the outcome or an answer. */
    @Override
    public String toString() {
        return "FreshnessRecord[id=" + id + ", listingId=" + listingId + ", listingRowVersion=" + listingRowVersion
                + "]";
    }
}
