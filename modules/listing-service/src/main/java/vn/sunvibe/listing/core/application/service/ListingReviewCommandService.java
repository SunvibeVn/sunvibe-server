package vn.sunvibe.listing.core.application.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.command.RevisionDecisionCommand;
import vn.sunvibe.listing.core.application.command.UnitLocationOpenCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.exception.ReviewerNotIndependentException;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.query.UnitLocationResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAddressFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitInventoryPort;
import vn.sunvibe.listing.core.application.usecase.ListingReviewCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingAccessLog;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.ReviewNote;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;
import vn.sunvibe.listing.core.domain.model.listing.RevisionDecision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.policy.listing.ListingReviewPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.RevisionTermsMatchPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingAccessLogDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the review's commands (listing-publish US-4 AC-2 to AC-5, BR-4, BR-6, BR-7; E06-S4 spec
 * sections 3.3, 3.4, E06S4-D3 to D9, D12; plan E06S4-P1, P3, P6, P8). One read-write transaction each, with one
 * {@code now}. The decision checks, in this order (E06S4-D5; the body and the role were checked at the edge): the
 * listing; archived, stored or because its agreement ended (a standing E05 no longer returns reads as ended); the data
 * version; the revision; its status; the reviewer's independence from its authors and from the assigned Sale read from
 * E05 now; for an approval only, the terms check against E05's versions read without a lock. The first failing check
 * answers. The location runs the same checks without a data version, then property's address and one access log row.
 * Nothing else: no outbox event, no row of another module, no listing state change, no share lock on E05, and no log
 * line holds a note, a reason, the terms or the location.
 */
@Service
@Transactional
public class ListingReviewCommandService implements ListingReviewCommandUseCase {

    private static final Pattern ID = Pattern.compile("^[0-9A-Z]{26}$");

    private final ListingDomainRepository listings;
    private final ListingAccessLogDomainRepository accessLogs;
    private final AdvertisingRightPort rights;
    private final UnitInventoryPort units;
    private final IdGenerator<String> ids;
    private final IdGenerator<Long> logIds;

    /** The access log's id is a Snowflake the application draws (ADR-006, E06S4-D18). */
    public ListingReviewCommandService(ListingDomainRepository listings, ListingAccessLogDomainRepository accessLogs,
                                       AdvertisingRightPort rights, UnitInventoryPort units, IdGenerator<String> ids,
                                       @Qualifier("Snowflake") IdGenerator<Long> logIds) {
        this.listings = listings;
        this.accessLogs = accessLogs;
        this.rights = rights;
        this.units = units;
        this.ids = ids;
        this.logIds = logIds;
    }

    /**
     * listing-publish US-4 AC-3 to AC-5 (E06S4-D5 to D9, plan E06S4-P1): the steps (3) to (9) of E06S4-D5 from the listing
     * row, the data version compared again with the loaded working set, so a command committed between the two reads is
     * refused and nothing is decided on a newer version than the one the reviewer read; then the aggregate's decision and
     * the guarded write.
     */
    @Override
    public RevisionCommandResult decide(RevisionDecisionCommand command, ListingViewer viewer) {
        Instant now = now();
        ListingRow row = rowOf(command.listingId());
        Optional<AgreementStandingFacts> standing = standingOf(row.agreementId());
        requireNotArchived(row, standing);
        // listing BR-3: a command acts on the version its caller read
        if (command.dataVersion() != row.rowVersion()) {
            throw new ListingChangedException();
        }
        int revisionNo = revisionNoOf(command.revisionNoOrNull());
        Listing listing = listings.findForReview(row.listingId(), revisionNo)
                .orElseThrow(ListingNotFoundException::new);
        ListingRevision target = listing.revision(revisionNo).orElseThrow(ListingNotFoundException::new);
        if (listing.rowVersion() != command.dataVersion()) {
            throw new ListingChangedException();
        }
        requirePending(target);
        requireIndependent(viewer, target, standing);
        // listing-publish BR-4, US-4 AC-5: an approval only of terms equal to a version of the agreement; a return or a
        // rejection reads no terms, so a reviewer can still return a revision whose terms differ
        if (command.outcome() == ReviewOutcome.APPROVED) {
            TermsMatch match = termsMatch(target, row, now);
            if (match != TermsMatch.IN_EFFECT && match != TermsMatch.PENDING_START) {
                throw ListingRefusedException.termsMismatch();
            }
        }
        RevisionDecision decision = decisionOf(command, target, viewer.userId(), now);
        listing.decide(revisionNo, decision, ids.generateId(), ids.generateId(), now);
        listings.update(listing);
        return new RevisionCommandResult(row.listingId(), revisionNo, command.outcome().status(),
                listing.rowVersion() + 1);
    }

    /**
     * listing-publish US-4 AC-2 (E06S4-D12, plan E06S4-P8): the steps of the decision without a data version, then
     * property's address of the listing's unit (none is a broken contract: nothing written), then one access log row
     * with no location value, in this transaction, so a failed log write answers no location.
     */
    @Override
    public UnitLocationResult openUnitLocation(UnitLocationOpenCommand command, ListingViewer viewer) {
        Instant now = now();
        ListingRow row = rowOf(command.listingId());
        Optional<AgreementStandingFacts> standing = standingOf(row.agreementId());
        requireNotArchived(row, standing);
        int revisionNo = revisionNoOf(command.revisionNoOrNull());
        ListingRevision revision = listings.findRevision(row.listingId(), revisionNo)
                .orElseThrow(ListingNotFoundException::new);
        requirePending(revision);
        requireIndependent(viewer, revision, standing);
        UnitAddressFacts address = units.address(row.unitId())
                .orElseThrow(() -> new IllegalStateException("property answers no location for the listing's unit"));
        accessLogs.record(ListingAccessLog.unitLocationOpened(logIds.generateId(), viewer.userId(), row.listingId(),
                revisionNo, now));
        return new UnitLocationResult(address.unitCodeOrNull(), address.locationLabelOrNull(), address.streetOrNull(),
                address.wardNameOrNull());
    }

    /** Step (3): a malformed id reads nothing; an unknown listing answers the one "not found" (listing BR-5). */
    private ListingRow rowOf(String listingId) {
        if (listingId == null || !ID.matcher(listingId).matches()) {
            throw new ListingNotFoundException();
        }
        return listings.findRow(listingId).orElseThrow(ListingNotFoundException::new);
    }

    /** Step (4), listing-publish US-9 AC-3: an archived listing takes no command, its archive stored or effective. */
    private static void requireNotArchived(ListingRow row, Optional<AgreementStandingFacts> standing) {
        // listing-publish E06-S1 spec E06S1-D5, plan E06S4-P3: a standing E05 no longer returns reads as ended
        boolean ended = standing.map(AgreementStandingFacts::ended).orElse(true);
        if (row.storedState().effective(ended) == ListingState.ARCHIVED) {
            throw ListingRefusedException.archived();
        }
    }

    /** Step (6): a malformed number arrives as null and reads as not found. */
    private static int revisionNoOf(Integer revisionNoOrNull) {
        if (revisionNoOrNull == null) {
            throw new ListingNotFoundException();
        }
        return revisionNoOrNull;
    }

    /** Step (7): only a Pending review revision is decided or has its location opened. */
    private static void requirePending(ListingRevision revision) {
        if (revision.status() != RevisionStatus.PENDING_REVIEW) {
            throw new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_PENDING);
        }
    }

    /**
     * Step (8), listing-publish BR-7, US-4 AC-4; listing BR-16 (E06S4-D3, D4, plan E06S4-P6): a reviewer who is none of
     * the revision's authors, through the copy chain, and not the assigned Sale read from E05 now, whatever roles they
     * hold. Any reason refuses, so a call that bypassed the role gate is refused too.
     */
    private void requireIndependent(ListingViewer viewer, ListingRevision revision,
                                    Optional<AgreementStandingFacts> standing) {
        Set<String> authors = listings.findAuthors(Set.of(revision.id())).getOrDefault(revision.id(), Set.of());
        String assignee = standing.map(AgreementStandingFacts::basisLeadAssigneeIdOrNull).orElse(null);
        List<ReviewIneligibility> reasons = ListingReviewPolicy.ineligibility(viewer.expandedAuthorities(),
                viewer.userId(), assignee, authors, revision.status(), false);
        if (!reasons.isEmpty()) {
            throw new ReviewerNotIndependentException(reasons);
        }
    }

    /**
     * Step (9), E06S4-D7: the revision's snapshot against E05's version in effect and its version waiting to start, read
     * without a lock and converted as the snapshot command converts them; no answer matches nothing.
     */
    private TermsMatch termsMatch(ListingRevision revision, ListingRow row, Instant now) {
        Optional<AgreementTermsFacts> answer = rights.terms(row.unitId(), row.agreementId());
        if (answer.isEmpty()) {
            return RevisionTermsMatchPolicy.match(revision.termsOrNull(), null, null);
        }
        AgreementTermsFacts facts = answer.get();
        RevisionTerms inEffect = facts.inEffectOrNull() == null ? null
                : ListingCommandService.snapshotOf(facts.inEffectOrNull(), TermsSource.IN_EFFECT, null, now);
        RevisionTerms pending = facts.pendingOrNull() == null ? null
                : ListingCommandService.snapshotOf(facts.pendingOrNull(), TermsSource.PENDING_START,
                facts.pendingStartDateOrNull(), now);
        return RevisionTermsMatchPolicy.match(revision.termsOrNull(), inEffect, pending);
    }

    /** The decision of the command's outcome; the domain holds its shape a second time (E06S4-D6). */
    private static RevisionDecision decisionOf(RevisionDecisionCommand command, ListingRevision target, String deciderId,
                                               Instant now) {
        return switch (command.outcome()) {
            case APPROVED -> RevisionDecision.approved(target.id(), target.revisionNo(), command.checks(), deciderId,
                    now);
            case NEEDS_CHANGES -> RevisionDecision.needsChanges(target.id(), target.revisionNo(),
                    command.changeReasons(), ReviewNote.ofOrNull(command.noteOrNull()), deciderId, now);
            case REJECTED -> RevisionDecision.rejected(target.id(), target.revisionNo(), command.rejectionGroupOrNull(),
                    ReviewNote.ofOrNull(command.noteOrNull()), deciderId, now);
        };
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
