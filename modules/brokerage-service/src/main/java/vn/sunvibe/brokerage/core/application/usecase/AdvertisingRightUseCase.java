package vn.sunvibe.brokerage.core.application.usecase;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Use Case - whether Sunvibe may advertise units now under their agreements, group (ii) of the listing brief, and a
 * locking read of one agreement (brokerage-agreement BR-12, BR-13, US-4 AC-1, AC-2; listing section 7, LM-16; ADR-012
 * decision 2, D12-13; E05-S6 spec S6-D9 to S6-D11). Each answer is computed at the call and never stored.
 * <p>
 * Viewer-less. Called only by listing's ports through an adapter in {@code vn.sunvibe.integration}, after listing
 * checked its own visibility, or by an anonymous public read that shows none of these values except through the
 * listing's own terms snapshot (ADR-012 decision 1); never exposed over HTTP (ADR-009; ViewerlessReadArchTest). A pair
 * whose agreement does not exist or belongs to another unit is left out: its consumer reads absence as "not allowed"
 * (fail closed). A failing read of another module reaches the caller as its exception, never as an answer.
 * <p>
 * Once an agreement ended (brokerage-agreement E05-S8 spec S8-D13), {@code endDate} and {@code endsAt} stay the planned
 * end of the version that counts, never the moment the agreement ended: a terminated agreement keeps its version's
 * planned end, and one terminated before its activated version started has no version that counts and answers both
 * {@code null}. A consumer reads {@code ended} (and the group {@code ENDED}) to know that the agreement ended.
 */
public interface AdvertisingRightUseCase {

    /** The most pairs, agreement ids or unit ids one call takes, counted after nulls and repeats are dropped. */
    int MAX_PAIRS = 250;

    /**
     * The answer for each pair, read-only, joining the caller's transaction (or none), at a fixed number of statements
     * whatever the count, with no lock. Null elements and repeats are dropped first.
     *
     * @throws IllegalArgumentException for more than {@link #MAX_PAIRS} distinct pairs, before any statement
     */
    List<AdvertisingRightView> answers(Collection<AdvertisingPair> pairs);

    /**
     * The unfinished agreement of each of those units and that transaction type code (brokerage-agreement BR-1: at most
     * one each); an unknown type answers empty.
     *
     * @throws IllegalArgumentException for more than {@link #MAX_PAIRS} distinct unit ids, before any statement
     */
    List<UnfinishedAgreementView> unfinishedOf(Collection<String> unitIds, String transactionType);

    /**
     * Group (ii) of each of those agreements: its unit and type, whether it ended, and its basis lead's current assignee;
     * an unknown agreement is left out.
     *
     * @throws IllegalArgumentException for more than {@link #MAX_PAIRS} distinct ids, before any statement
     */
    List<AgreementStandingView> standings(Collection<String> agreementIds);

    /**
     * The answer for one pair read after a FOR SHARE lock on the agreement row, held to the end of the caller's
     * transaction (ADR-012 decision 2 "Commands", D12-13): a stop or activation that starts while it is held waits on its
     * guarded update until the caller commits. Lock order: the listing's row, then the agreement row. Empty when the
     * agreement does not exist or belongs to another unit.
     *
     * @throws IllegalStateException when called with no transaction, or inside a read-only one (PostgreSQL refuses the
     *                               lock there), before any statement; one exception type for both misuses
     */
    Optional<AdvertisingRightView> answerUnderShareLock(AdvertisingPair pair);
}
