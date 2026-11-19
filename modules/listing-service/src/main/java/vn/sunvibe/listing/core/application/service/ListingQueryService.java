package vn.sunvibe.listing.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.AgreementListingsView;
import vn.sunvibe.listing.core.application.query.ListingView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.usecase.ListingQueryUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.ListingAccessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingCreatePolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRevisionPage;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ListingRowPage;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the staff reads of listings (listing-publish E06-S1 spec 3.3, E06S1-P11). Read-only: they
 * write nothing, read the agreement's standing once, and answer bounded lists (E06S1-D17). The scope is checked before
 * any listing data is read past the row that names the agreement; out of scope answers the same as unknown (listing
 * BR-5).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ListingQueryService implements ListingQueryUseCase {

    /** listing-publish E06-S1 spec E06S1-D17, assumption A-1: the newest revisions and listings a read answers. */
    static final int NEWEST = 20;

    private static final Pattern ID = Pattern.compile("^[0-9A-Z]{26}$");
    private static final List<String> NOT_STARTED_ALONE = List.of(AgreementReasonGroup.NOT_STARTED.name());

    private final ListingDomainRepository listings;
    private final AdvertisingRightPort rights;

    @Override
    public ListingView listing(String listingId, ListingViewer viewer) {
        requireWellFormed(listingId);
        ListingRow row = listings.findRow(listingId).orElseThrow(ListingNotFoundException::new);
        // listing-publish E06-S1 spec E06S1-D5: a standing E05 no longer returns reads as ended, with no assignee
        Optional<AgreementStandingFacts> standing = standingOf(row.agreementId());
        boolean ended = standing.map(AgreementStandingFacts::ended).orElse(true);
        String assignee = standing.map(AgreementStandingFacts::basisLeadAssigneeIdOrNull).orElse(null);
        // listing BR-5, BR-16: reviewers and managers read every listing, a SALE only while the current assignee
        if (!ListingAccessPolicy.mayReadListing(viewer.expandedAuthorities(), viewer.userId(), assignee)) {
            throw new ListingNotFoundException();
        }
        ListingRevisionPage page = listings.findNewestRevisions(listingId, NEWEST);
        return new ListingView(row.listingId(), row.code().value(), row.storedState().effective(ended),
                row.transactionType(), row.unitId(), row.agreementId(), row.createdAt(), row.rowVersion(),
                isAssignee(viewer, assignee),
                page.revisions().stream()
                        .map(revision -> new ListingView.Revision(revision.revisionNo(), revision.status(),
                                revision.createdAt()))
                        .toList(),
                page.hasOlder());
    }

    @Override
    public AgreementListingsView ofAgreement(String agreementId, ListingViewer viewer) {
        requireWellFormed(agreementId);
        AgreementStandingFacts standing = standingOf(agreementId).orElseThrow(ListingNotFoundException::new);
        String assignee = standing.basisLeadAssigneeIdOrNull();
        // E05's own agreement page scope (brokerage-agreement brief section 2): managers every agreement, a SALE only
        // while the current assignee; LISTING_REVIEWER has no right here
        if (!ListingAccessPolicy.mayReadAgreementListings(viewer.expandedAuthorities(), viewer.userId(), assignee)) {
            throw new ListingNotFoundException();
        }
        ListingRowPage page = listings.findNewestByAgreement(agreementId, NEWEST);
        boolean hasOpen = listings.hasOpen(agreementId);
        Optional<AdvertisingAnswerFacts> answer = rights.answer(standing.unitId(), agreementId);
        List<AgreementReasonGroup> reasonGroups = ListingCreatePolicy.refusals(standing.ended(),
                answer.map(ListingQueryService::toDomain).orElse(null));
        // the same steps as the create's after the request shape (spec 3.3); the command decides
        boolean viewerMayCreate = ListingAccessPolicy.mayCreate(viewer.expandedAuthorities(), viewer.userId(), assignee)
                && TransactionType.LONG_TERM_RENT.code().equals(standing.transactionType())
                && !hasOpen
                && reasonGroups.isEmpty();
        boolean advertisingNotStarted = answer.map(facts -> facts.reasonGroups().equals(NOT_STARTED_ALONE))
                .orElse(false);
        return new AgreementListingsView(agreementId,
                page.rows().stream()
                        .map(row -> new AgreementListingsView.Item(row.listingId(), row.code().value(),
                                row.storedState().effective(standing.ended()), row.createdAt()))
                        .toList(),
                page.hasOlder(),
                new AgreementListingsView.Creation(viewerMayCreate, advertisingNotStarted, reasonGroups));
    }

    private Optional<AgreementStandingFacts> standingOf(String agreementId) {
        return rights.standings(List.of(agreementId)).stream()
                .filter(standing -> agreementId.equals(standing.agreementId()))
                .findFirst();
    }

    /** A malformed id answers as an unknown one, before any read (spec 3.3). */
    private static void requireWellFormed(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new ListingNotFoundException();
        }
    }

    private static boolean isAssignee(ListingViewer viewer, String assigneeIdOrNull) {
        return assigneeIdOrNull != null && assigneeIdOrNull.equals(viewer.userId());
    }

    /** E05's codes as listing's domain values; a code listing does not know fails (E06S1-D9), never read as allowed. */
    private static ListingCreatePolicy.AnswerFacts toDomain(AdvertisingAnswerFacts facts) {
        return new ListingCreatePolicy.AnswerFacts(facts.ended(), facts.allowed(),
                facts.reasonGroups().stream().map(AgreementReasonGroup::ofCode).toList());
    }
}
