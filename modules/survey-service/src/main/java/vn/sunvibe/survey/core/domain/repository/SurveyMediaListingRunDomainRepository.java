package vn.sunvibe.survey.core.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import vn.sunvibe.survey.core.domain.model.media.ListingRun;

/**
 * The runs of the full listing (survey-media spec 4.4 step 4, A-18): one open run at a time (a partial unique index),
 * leased and fenced, so a killed run resumes from its stored key and a fenced-out runner writes nothing.
 */
public interface SurveyMediaListingRunDomainRepository {

    /** Leases the open run when its lease ran out (or it has none); empty when there is none or it is leased. */
    Optional<ListingRun> leaseOpen(String token, Instant leaseUntil, Instant now);

    /**
     * Starts a leased run when none is open and none started after {@code dueBefore} (a daily run); empty otherwise,
     * also when another instance started one first.
     */
    Optional<ListingRun> startIfDue(String token, Instant leaseUntil, Instant dueBefore, Instant now);

    /** Fenced progress of one page: the key listed up to, keys seen and orphans not deleted added, the lease renewed. */
    boolean progress(long id, String token, String startAfterKey, int keysSeen, int undeleted, Instant leaseUntil);

    /** Fenced: the run listed to the end. */
    boolean finish(long id, String token, Instant now);

    /** Runs open since before the cutoff are abandoned (finished, counting for nothing); returns their ids. */
    List<Long> abandonStartedBefore(Instant cutoff, Instant now);

    /**
     * survey-media spec A-18, SM-14: the open run, or the latest finished run when it listed to the end, has orphans it
     * could not delete; a resume waits until the next run finishes (an open newer run has not reached them yet; an
     * abandoned run blocks nothing, so no run blocks the resume for ever).
     */
    boolean blocksResume();
}
