package vn.sunvibe.acquisition.core.application.shared;

import java.util.Optional;

/**
 * Reads the owner's asking price out of a lead's submission snapshot, the intake request as the owner sent it
 * (acquisition-owner-intake BR-5). Survey checklists compare an "owner's form" rent with it, because a requirement
 * change replaces the lead's own price (survey-checklist spec A-18, A-27). Nothing else of the snapshot is read.
 */
public interface SubmissionSnapshotReader {

    /** The submitted asking price; empty when the snapshot holds none or cannot be read as one. */
    Optional<SubmittedPrice> askingPrice(String snapshot);

    /** A submitted price: whole VND and its unit name (NIGHT, MONTH, ...). */
    record SubmittedPrice(long amount, String unit) {
    }
}
