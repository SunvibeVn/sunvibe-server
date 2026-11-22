package vn.sunvibe.listing.core.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.command.PutLiveCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.usecase.ListingPublicationCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.PutLiveMoment;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.policy.listing.ListingAccessPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.outbox.EventOutboxPort;
import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the assigned Sale's put live (listing-publish US-5 AC-1, AC-3, AC-4, E-8; E06-S6 spec
 * E06S6-D5, D8). One read-write transaction with one {@code now}. The checks, in this order, the first failing one
 * answering (the body and the role were checked at the edge): the listing and its agreement's standing naming the caller
 * as the assignee; not archived, stored or effective; the data version against the row; the revision, in the working set
 * of the target, the Approved and the Live revisions, whose version is compared again; the target Approved; the listing
 * row locked {@code FOR UPDATE} and its locked version compared again, before E05's share lock on the agreement row
 * (lock order: the listing row, then the agreement row); then the display facts and the aggregate's try. Any display
 * reason refuses with nothing written; otherwise the one guarded write, guarded by the version the caller sent, and the
 * listing's event. No log line holds a fact, a term or a reason of E05.
 */
@Service
@Transactional
public class ListingPublicationCommandService implements ListingPublicationCommandUseCase {

    private static final Pattern ID = Pattern.compile("^[0-9A-Z]{26}$");

    private final ListingDomainRepository listings;
    private final AdvertisingRightPort rights;
    private final DisplayConditionsAssembler assembler;
    private final EventOutboxPort events;
    private final IdGenerator<String> ids;

    public ListingPublicationCommandService(ListingDomainRepository listings, AdvertisingRightPort rights,
                                            DisplayConditionsAssembler assembler, EventOutboxPort events,
                                            IdGenerator<String> ids) {
        this.listings = listings;
        this.rights = rights;
        this.assembler = assembler;
        this.events = events;
        this.ids = ids;
    }

    @Override
    public RevisionCommandResult putLive(PutLiveCommand command, ListingViewer viewer) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        long version = command.dataVersion();
        // step (3), listing BR-5, BR-16: a malformed or unknown listing, or a caller who is not the assigned SALE or
        // SALE_LEADER, answers the one "not found"
        ListingRow row = rowOf(command.listingId());
        AgreementStandingFacts standing = rights.standings(List.of(row.agreementId())).stream()
                .filter(found -> row.agreementId().equals(found.agreementId()))
                .findFirst()
                .filter(found -> ListingAccessPolicy.mayPutLive(viewer.expandedAuthorities(), viewer.userId(),
                        found.basisLeadAssigneeIdOrNull()))
                .orElseThrow(ListingNotFoundException::new);
        // step (4), listing-publish US-9 AC-3: an archived listing takes no command, its archive stored or effective
        if (row.storedState().effective(standing.ended()) == ListingState.ARCHIVED) {
            throw ListingRefusedException.archived();
        }
        // step (5), listing BR-3: a command acts on the version its caller read
        if (row.rowVersion() != version) {
            throw new ListingChangedException();
        }
        // step (6): a malformed or unknown revision number answers "not found"; the working set read after the row
        // must still be on that version
        if (command.revisionNoOrNull() == null) {
            throw new ListingNotFoundException();
        }
        int revisionNo = command.revisionNoOrNull();
        Listing listing = listings.findForPutLive(row.listingId(), revisionNo)
                .orElseThrow(ListingNotFoundException::new);
        ListingRevision target = listing.revision(revisionNo).orElseThrow(ListingNotFoundException::new);
        if (listing.rowVersion() != version) {
            throw new ListingChangedException();
        }
        // step (7), listing-publish US-5 AC-1: only an Approved revision goes live
        if (target.status() != RevisionStatus.APPROVED) {
            throw new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_APPROVED);
        }
        // step (8), E06S6-D8: the listing row locked before E05's share lock, and nothing committed since the reads
        long locked = listings.lockRow(row.listingId()).orElseThrow(ListingChangedException::new);
        if (locked != version) {
            throw new ListingChangedException();
        }
        // step (9), listing BR-4, listing-publish BR-11: the display facts with E05 read under its share lock
        DisplayConditions conditions = assembler.underShareLock(row, listing.state(), now);
        List<DisplayReason> reasons = listing.tryPutLive(revisionNo, conditions, PutLiveMoment.SALE_COMMAND,
                viewer.userId(), now, ids.generateId(), ids.generateId());
        if (!reasons.isEmpty()) {
            throw ListingRefusedException.putLiveRefused(reasons,
                    reasons.contains(DisplayReason.ADVERTISING_RIGHT) ? conditions.agreementGroups() : List.of());
        }
        // step (10): the one guarded write, then the listing's event (ADR-014 decision 3)
        listings.update(listing);
        events.append(ListingChangedEvent.now(row.listingId()));
        return new RevisionCommandResult(row.listingId(), revisionNo, RevisionStatus.LIVE, version + 1);
    }

    private ListingRow rowOf(String listingId) {
        if (listingId == null || !ID.matcher(listingId).matches()) {
            throw new ListingNotFoundException();
        }
        return listings.findRow(listingId).orElseThrow(ListingNotFoundException::new);
    }
}
