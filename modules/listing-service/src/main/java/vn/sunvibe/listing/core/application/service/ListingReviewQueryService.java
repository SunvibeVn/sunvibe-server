package vn.sunvibe.listing.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ReviewQueueView;
import vn.sunvibe.listing.core.application.query.RevisionDecisionView;
import vn.sunvibe.listing.core.application.query.RevisionReviewView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitInventoryPort;
import vn.sunvibe.listing.core.application.usecase.ListingReviewQueryUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.RevisionDecision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingAccessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingReviewPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.RevisionTermsMatchPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ReviewCandidate;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the review's reads (listing-publish US-4 AC-1, AC-2, AC-4 to AC-6; E06-S4 spec sections 3.1,
 * 3.2, 3.5, E06S4-D2, D10, D11, D15; plan E06S4-P3, P5, P6, P7). Read-only: they write nothing and never read the real
 * location. The queue and the review read answer only the four roles that read reviews (the controller's gate, held a
 * second time here); the decision read answers whoever reads the listing. Out of scope answers as unknown (listing BR-5).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ListingReviewQueryService implements ListingReviewQueryUseCase {

    /** listing-publish E06-S4 spec E06S4-D10, assumption A-1: the rows the queue answers. */
    static final int QUEUE_SHOWN = 50;
    /** E06S4-D10: the candidates evaluated with one E05 standings read and one authors statement, E05's bound of ids. */
    static final int QUEUE_CHUNK = 250;
    /** E06S4-D10, assumption A-1: at most four chunks (1,000 candidates) per read. */
    static final int QUEUE_CHUNKS = 4;

    private static final Pattern ID = Pattern.compile("^[0-9A-Z]{26}$");

    private final ListingDomainRepository listings;
    private final AdvertisingRightPort rights;
    private final UnitInventoryPort units;

    /**
     * listing-publish US-4 AC-1 (E06S4-D10, plan E06S4-P5): the Pending review revisions of listings not stored as
     * archived, oldest submission first, by keyset in chunks of {@value #QUEUE_CHUNK}, each chunk evaluated with one E05
     * standings read and one authors statement. A row whose agreement ended, or whose standing E05 no longer returns, is
     * left out; every other row carries the viewer's reasons. Stops at the 51st kept row, at a short chunk or after
     * {@value #QUEUE_CHUNKS} chunks; one snapshot for every chunk.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReviewQueueView queue(ListingViewer viewer) {
        // plan E06S4-P6: a viewer outside the four roles that read reviews reads no candidate
        if (!ListingAccessPolicy.mayReadReviews(viewer.expandedAuthorities())) {
            return new ReviewQueueView(List.of(), false);
        }
        List<ReviewQueueView.Item> kept = new ArrayList<>();
        ReviewCandidate cursor = null;
        boolean hasMore = false;
        for (int chunk = 1; chunk <= QUEUE_CHUNKS; chunk++) {
            List<ReviewCandidate> candidates = cursor == null
                    ? listings.findReviewCandidates(null, null, QUEUE_CHUNK)
                    : listings.findReviewCandidates(cursor.submittedAt(), cursor.revisionId(), QUEUE_CHUNK);
            if (candidates.isEmpty()) {
                break;
            }
            Map<String, AgreementStandingFacts> standings = new HashMap<>();
            rights.standings(candidates.stream().map(ReviewCandidate::agreementId).distinct().toList())
                    .forEach(standing -> standings.putIfAbsent(standing.agreementId(), standing));
            Map<String, Set<String>> authors = listings.findAuthors(
                    candidates.stream().map(ReviewCandidate::revisionId).toList());
            for (ReviewCandidate candidate : candidates) {
                AgreementStandingFacts standing = standings.get(candidate.agreementId());
                // listing-publish E06-S1 spec E06S1-D5, plan E06S4-P3: an ended agreement, or a standing no longer
                // returned, archives the listing; its revision cannot be decided
                if (standing == null || standing.ended()) {
                    continue;
                }
                kept.add(itemOf(candidate, ListingReviewPolicy.ineligibility(viewer.expandedAuthorities(),
                        viewer.userId(), standing.basisLeadAssigneeIdOrNull(),
                        authors.getOrDefault(candidate.revisionId(), Set.of()), RevisionStatus.PENDING_REVIEW,
                        false)));
                if (kept.size() > QUEUE_SHOWN) {
                    return new ReviewQueueView(kept.subList(0, QUEUE_SHOWN), true);
                }
            }
            if (candidates.size() < QUEUE_CHUNK) {
                break;
            }
            cursor = candidates.getLast();
            hasMore = chunk == QUEUE_CHUNKS;
        }
        return new ReviewQueueView(kept, hasMore);
    }

    /**
     * listing-publish US-4 AC-2, AC-4, AC-5 (E06S4-D11, plan E06S4-P3, P6, P7): the review context of one revision of
     * any status: the viewer's reasons, E05's answer for the listing's pair, the terms check, property's availability of
     * the unit, the freshness summary and the checklist. Never the real location (E06S4-D12). A code the module does not
     * know fails the read. One snapshot for the whole answer, so the data version belongs to the status beside it.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RevisionReviewView review(String listingId, Integer revisionNoOrNull, ListingViewer viewer) {
        // plan E06S4-P6: a viewer outside the four roles that read reviews gets the one "not found", before any read
        if (!ListingAccessPolicy.mayReadReviews(viewer.expandedAuthorities())) {
            throw new ListingNotFoundException();
        }
        ListingRow row = rowOf(listingId);
        int revisionNo = revisionNoOf(revisionNoOrNull);
        ListingRevision revision = listings.findRevision(row.listingId(), revisionNo)
                .orElseThrow(ListingNotFoundException::new);
        Optional<AgreementStandingFacts> standing = standingOf(row.agreementId());
        boolean ended = standing.map(AgreementStandingFacts::ended).orElse(true);
        String assignee = standing.map(AgreementStandingFacts::basisLeadAssigneeIdOrNull).orElse(null);
        Set<String> authors = listings.findAuthors(Set.of(revision.id())).getOrDefault(revision.id(), Set.of());
        List<ReviewIneligibility> reasons = ListingReviewPolicy.ineligibility(viewer.expandedAuthorities(),
                viewer.userId(), assignee, authors, revision.status(),
                row.storedState().effective(ended) == ListingState.ARCHIVED);
        Instant now = now();
        Optional<AdvertisingReviewFacts> answer = rights.review(row.unitId(), row.agreementId());
        RevisionReviewView.Advertising advertising = answer.map(facts -> advertisingOf(facts, now)).orElse(null);
        TermsMatch termsMatch = advertising == null
                ? RevisionTermsMatchPolicy.match(revision.termsOrNull(), null, null)
                : RevisionTermsMatchPolicy.match(revision.termsOrNull(), advertising.termsInEffectOrNull(),
                advertising.pendingOrNull());
        return new RevisionReviewView(row.listingId(), row.code().value(), revision.revisionNo(), revision.status(),
                row.rowVersion(), reasons.isEmpty(), reasons, advertising, termsMatch, unitOf(row.unitId()),
                freshnessOf(listings.findFreshnessSummary(row.listingId()), now), ReviewCheck.VERSION_1);
    }

    /**
     * listing-publish US-4 AC-6 (E06S4-D15, plan E06S4-P3): the decision of one revision for whoever reads the listing
     * (listing BR-5, BR-16: reviewers and managers every listing, a SALE only while the current assignee); a revision
     * without a decision answers the one "not found". No decider is answered.
     */
    @Override
    public RevisionDecisionView decision(String listingId, Integer revisionNoOrNull, ListingViewer viewer) {
        ListingRow row = rowOf(listingId);
        // a standing E05 no longer returns has no assignee
        String assignee = standingOf(row.agreementId()).map(AgreementStandingFacts::basisLeadAssigneeIdOrNull)
                .orElse(null);
        if (!ListingAccessPolicy.mayReadListing(viewer.expandedAuthorities(), viewer.userId(), assignee)) {
            throw new ListingNotFoundException();
        }
        int revisionNo = revisionNoOf(revisionNoOrNull);
        RevisionDecision decision = listings.findDecision(row.listingId(), revisionNo)
                .orElseThrow(ListingNotFoundException::new);
        return new RevisionDecisionView(decision.outcome(), decision.decidedAt(), decision.checklistVersionOrNull(),
                decision.changeReasons(), decision.rejectionGroupOrNull(),
                decision.noteOrNull() == null ? null : decision.noteOrNull().text());
    }

    private static ReviewQueueView.Item itemOf(ReviewCandidate candidate, List<ReviewIneligibility> reasons) {
        return new ReviewQueueView.Item(candidate.listingId(), candidate.code(), candidate.revisionNo(),
                candidate.submittedAt(), candidate.title(), candidate.unitOrNull(), reasons.isEmpty(), reasons);
    }

    /**
     * E05's answer mapped as the module reads it (plan E06S4-P7): the reason groups through
     * {@link AgreementReasonGroup#ofCode}, the two versions as the snapshot command converts them; an unknown code fails.
     */
    private static RevisionReviewView.Advertising advertisingOf(AdvertisingReviewFacts facts, Instant now) {
        RevisionTerms inEffect = ListingCommandService.inEffectSnapshotOrNull(facts.inEffectOrNull(), now);
        RevisionTerms pending = facts.pendingOrNull() == null ? null
                : ListingCommandService.snapshotOf(facts.pendingOrNull(), TermsSource.PENDING_START,
                facts.pendingStartDateOrNull(), now);
        return new RevisionReviewView.Advertising(facts.allowed(), facts.ended(),
                facts.reasonGroups().stream().map(AgreementReasonGroup::ofCode).toList(), facts.endDateOrNull(),
                facts.interiorPhotos(), facts.exteriorPhotos(), inEffect, facts.pendingStartDateOrNull(), pending);
    }

    /** property's answer for the unit; none when property answers no unit; an unknown availability fails. */
    private RevisionReviewView.Unit unitOf(String unitId) {
        return units.availability(List.of(unitId)).stream()
                .filter(facts -> unitId.equals(facts.unitId()))
                .findFirst()
                .map(ListingReviewQueryService::unitOf)
                .orElse(new RevisionReviewView.Unit(null, null, false));
    }

    private static RevisionReviewView.Unit unitOf(UnitAvailabilityFacts facts) {
        return new RevisionReviewView.Unit(facts.unitCodeOrNull(), UnitAvailability.ofCode(facts.availabilityOrNull()),
                facts.availabilityDiffers());
    }

    /** listing-publish E06-S3 spec E06S3-D5: the summary's dates and flag as the freshness read computes them. */
    private static RevisionReviewView.Freshness freshnessOf(FreshnessSummary summary, Instant now) {
        LocalDate confirmedOn = summary.confirmedOnOrNull();
        return new RevisionReviewView.Freshness(confirmedOn,
                confirmedOn == null ? null : FreshnessPolicy.validThrough(confirmedOn),
                FreshnessPolicy.deadlinePassed(summary, now), summary.latestValidOrNull());
    }

    /** A malformed id reads nothing; an unknown listing answers the one "not found" (listing BR-5). */
    private ListingRow rowOf(String listingId) {
        if (listingId == null || !ID.matcher(listingId).matches()) {
            throw new ListingNotFoundException();
        }
        return listings.findRow(listingId).orElseThrow(ListingNotFoundException::new);
    }

    /** A malformed number arrives as null and reads as not found. */
    private static int revisionNoOf(Integer revisionNoOrNull) {
        if (revisionNoOrNull == null) {
            throw new ListingNotFoundException();
        }
        return revisionNoOrNull;
    }

    private Optional<AgreementStandingFacts> standingOf(String agreementId) {
        return rights.standings(List.of(agreementId)).stream()
                .filter(standing -> agreementId.equals(standing.agreementId()))
                .findFirst();
    }

    private static Instant now() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
    }
}
