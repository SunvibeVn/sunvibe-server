package vn.sunvibe.listing.core.application.command;

/**
 * Save a Draft's title, description and features as a whole (listing-publish US-2 AC-1, AC-3; E06-S2 spec 3.2,
 * E06S2-D4): the texts in their stored form, as the request converter normalized them; null is empty.
 *
 * @param revisionNoOrNull the revision named in the path; null when the path did not name a well-formed number
 * @param dataVersion      the listing's row version the caller read (listing BR-3)
 */
public record RevisionSaveCommand(String listingId, Integer revisionNoOrNull, long dataVersion, String titleOrNull,
                                  String descriptionOrNull, RevisionFeaturesInput features) {

    /** listing-publish US-10 AC-4: unpublished text never reaches a log through this record. */
    @Override
    public String toString() {
        return "RevisionSaveCommand[hidden]";
    }
}
