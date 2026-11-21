package vn.sunvibe.listing.core.domain.policy.listing;

import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The freshness dates of a listing (listing-publish BR-10, BR-11; E06-S3 spec E06S3-D5), all in Vietnam time: from a
 * valid confirmation on day d, reconfirmation is due from the start of d+10, the last valid day is d+14, and the
 * deadline is the start of d+15. The reconfirmation flag stays on until a new valid confirmation. A listing never validly
 * confirmed has no flag, no passed deadline and nothing that holds. Pure: the caller passes {@code now}.
 */
public final class FreshnessPolicy {

    /** listing-publish BR-10: every freshness date is a Vietnam calendar date. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** listing-publish BR-10: reconfirmation is due 10 days after the confirmation. */
    public static final int RECONFIRM_AFTER_DAYS = 10;
    /** listing-publish BR-10: a confirmation holds for 14 days after its own day. */
    public static final int VALID_DAYS = 14;

    private FreshnessPolicy() {
    }

    /** The Vietnam calendar date of an instant. */
    public static LocalDate today(Instant now) {
        return now.atZone(ZONE).toLocalDate();
    }

    /** The first day on which a confirmation of that day is due again. */
    public static LocalDate reconfirmFrom(LocalDate confirmedOn) {
        return confirmedOn.plusDays(RECONFIRM_AFTER_DAYS);
    }

    /** The last day on which a confirmation of that day holds. */
    public static LocalDate validThrough(LocalDate confirmedOn) {
        return confirmedOn.plusDays(VALID_DAYS);
    }

    /** The instant a confirmation of that day stops holding: 00:00 Vietnam time of the day after the last valid day. */
    public static Instant deadline(LocalDate confirmedOn) {
        return validThrough(confirmedOn).plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /** A listing confirmed on or before this date is due for reconfirmation at {@code now}. */
    public static LocalDate dueOnOrBefore(Instant now) {
        return today(now).minusDays(RECONFIRM_AFTER_DAYS);
    }

    /** listing-publish BR-10: due from the start of the tenth day after the latest valid confirmation, with no end. */
    public static boolean reconfirmationDue(FreshnessSummary summary, Instant now) {
        LocalDate confirmedOn = summary.confirmedOnOrNull();
        return confirmedOn != null && !today(now).isBefore(reconfirmFrom(confirmedOn));
    }

    /** listing-publish BR-10: the latest valid confirmation's deadline has passed. */
    public static boolean deadlinePassed(FreshnessSummary summary, Instant now) {
        LocalDate confirmedOn = summary.confirmedOnOrNull();
        return confirmedOn != null && !now.isBefore(deadline(confirmedOn));
    }

    /**
     * listing-publish BR-11, condition (e) of publication: the latest record is a valid confirmation and its deadline
     * has not passed.
     */
    public static boolean holds(FreshnessSummary summary, Instant now) {
        LocalDate confirmedOn = summary.confirmedOnOrNull();
        return confirmedOn != null && Boolean.TRUE.equals(summary.latestValidOrNull())
                && now.isBefore(deadline(confirmedOn));
    }
}
