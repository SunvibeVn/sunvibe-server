package vn.sunvibe.listing.core.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ListingPublicPreviewView;
import vn.sunvibe.listing.core.application.query.ListingPublicationView;
import vn.sunvibe.listing.core.application.query.LiveDifferenceView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.usecase.ListingPublicationQueryUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingAccessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingDisplayPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the publication reads (listing-publish US-2 AC-7, US-4 AC-2, US-5 AC-3, BR-11; E06-S6 spec
 * E06S6-D2, D11, D12). Each read is one read-only transaction at {@code REPEATABLE_READ}, so the listing row, its
 * revisions, its freshness summary and the answers of E05 and property, whose reads join this transaction (ADR-009), come
 * from one snapshot. No lock: E05's review answer is read without its share lock. A code or terms listing does not know
 * fail the read, so nothing is shown. No log line holds a fact, a term or a reason of E05.
 */
@Service
@Transactional(readOnly = true)
public class ListingPublicationQueryService implements ListingPublicationQueryUseCase {

    private static final Pattern ID = Pattern.compile("^[0-9A-Z]{26}$");

    private final ListingDomainRepository listings;
    private final AdvertisingRightPort rights;
    private final DisplayConditionsAssembler assembler;

    public ListingPublicationQueryService(ListingDomainRepository listings, AdvertisingRightPort rights,
                                          DisplayConditionsAssembler assembler) {
        this.listings = listings;
        this.rights = rights;
        this.assembler = assembler;
    }

    /**
     * E06S6-D11: the scope of the listing read (listing BR-5, BR-16: reviewers and managers read every listing, a SALE
     * only while the current assignee); a listing read as archived, stored or because its agreement ended, answers only
     * that, reading nothing more; otherwise the live revision answered by the rule of a shown revision, the Approved one
     * by the rule of a put live, and the viewer's permissions.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ListingPublicationView publication(String listingId, ListingViewer viewer) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        ListingRow row = rowOf(listingId);
        // listing-publish E06-S1 spec E06S1-D5: a standing E05 no longer returns reads as ended, with no assignee
        Optional<AgreementStandingFacts> standing = rights.standings(List.of(row.agreementId())).stream()
                .filter(found -> row.agreementId().equals(found.agreementId()))
                .findFirst();
        boolean ended = standing.map(AgreementStandingFacts::ended).orElse(true);
        String assignee = standing.map(AgreementStandingFacts::basisLeadAssigneeIdOrNull).orElse(null);
        if (!ListingAccessPolicy.mayReadListing(viewer.expandedAuthorities(), viewer.userId(), assignee)) {
            throw new ListingNotFoundException();
        }
        ListingState state = row.storedState().effective(ended);
        // listing-publish US-9 AC-3: an archived listing takes no command, so it shows no revision and permits nothing
        if (state == ListingState.ARCHIVED) {
            return new ListingPublicationView(row.listingId(), state, row.rowVersion(), null, null, false, false);
        }
        Listing listing = listings.findForPutLive(row.listingId(), null).orElseThrow(ListingNotFoundException::new);
        DisplayConditions conditions = assembler.withoutLock(row, state, now);
        ListingPublicationView.Live live = revisionWith(listing, RevisionStatus.LIVE)
                .map(revision -> new ListingPublicationView.Live(revision.revisionNo(), wentLiveAt(revision),
                        answerOf(ListingDisplayPolicy.liveReasons(conditions, revision.termsOrNull(), now), conditions)))
                .orElse(null);
        ListingPublicationView.Approved approved = revisionWith(listing, RevisionStatus.APPROVED)
                .map(revision -> new ListingPublicationView.Approved(revision.revisionNo(), termsSourceOf(revision),
                        answerOf(ListingDisplayPolicy.putLiveReasons(conditions, revision.termsOrNull(), now),
                                conditions)))
                .orElse(null);
        // listing-publish E06-S6 spec E06S6-D2: the assigned Sale puts live an Approved revision of an Unpublished or
        // Published listing, and copies while no revision is open (E06S4-D14)
        boolean mayPutLive = approved != null
                && (state == ListingState.UNPUBLISHED || state == ListingState.PUBLISHED)
                && ListingAccessPolicy.mayPutLive(viewer.expandedAuthorities(), viewer.userId(), assignee);
        boolean mayCopy = ListingAccessPolicy.mayDraft(viewer.expandedAuthorities(), viewer.userId(), assignee)
                && listings.findOpenRevisionNo(row.listingId()).isEmpty();
        return new ListingPublicationView(row.listingId(), state, row.rowVersion(), live, approved, mayPutLive, mayCopy);
    }

    /**
     * E06S6-D12: the roles of the review read (a viewer outside them gets the one "not found" before any read), the
     * listing and the revision, then the asked revision against the live one: compared, no live revision, or the live
     * revision itself.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LiveDifferenceView liveDifference(String listingId, Integer revisionNoOrNull, ListingViewer viewer) {
        if (!ListingAccessPolicy.mayReadReviews(viewer.expandedAuthorities())) {
            throw new ListingNotFoundException();
        }
        ListingRow row = rowOf(listingId);
        if (revisionNoOrNull == null) {
            throw new ListingNotFoundException();
        }
        int revisionNo = revisionNoOrNull;
        Listing listing = listings.findForPutLive(row.listingId(), revisionNo)
                .orElseThrow(ListingNotFoundException::new);
        ListingRevision asked = listing.revision(revisionNo).orElseThrow(ListingNotFoundException::new);
        Optional<ListingRevision> live = revisionWith(listing, RevisionStatus.LIVE);
        if (live.isEmpty()) {
            return new LiveDifferenceView(revisionNo, LiveDifferenceView.Comparison.NO_LIVE_REVISION, null, List.of());
        }
        if (live.get().revisionNo() == revisionNo) {
            return new LiveDifferenceView(revisionNo, LiveDifferenceView.Comparison.IS_LIVE_REVISION, null, List.of());
        }
        ListingRevision liveRevision = live.get();
        // a live revision was approved and put live only with terms equal to a version of the agreement
        if (liveRevision.termsOrNull() == null) {
            throw new IllegalStateException("the live revision has no terms snapshot");
        }
        return new LiveDifferenceView(revisionNo, LiveDifferenceView.Comparison.COMPARED,
                new LiveDifferenceView.LiveRevision(liveRevision.revisionNo(), wentLiveAt(liveRevision),
                        liveRevision.content(), liveRevision.termsOrNull()),
                asked.changedPartsFrom(liveRevision));
    }

    /**
     * listing-publish E06-S7 spec E06S7-D9, US-2 AC-3: the scope of the revision read (listing BR-5: a SALE only while
     * the current assignee), then the asked revision with its fees, the listing row's code and the freshness summary's
     * date; any revision of the listing, as it would be published. Data only: no E05 condition, no property value, no
     * display rule.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ListingPublicPreviewView publicPreview(String listingId, Integer revisionNoOrNull, ListingViewer viewer) {
        ListingRow row = rowOf(listingId);
        String assignee = rights.standings(List.of(row.agreementId())).stream()
                .filter(found -> row.agreementId().equals(found.agreementId()))
                .findFirst()
                .map(AgreementStandingFacts::basisLeadAssigneeIdOrNull)
                .orElse(null);
        if (!ListingAccessPolicy.mayReadListing(viewer.expandedAuthorities(), viewer.userId(), assignee)
                || revisionNoOrNull == null) {
            throw new ListingNotFoundException();
        }
        ListingRevision revision = listings.findRevision(row.listingId(), revisionNoOrNull)
                .orElseThrow(ListingNotFoundException::new);
        UnitSnapshot unit = revision.unitSnapshotOrNull();
        RevisionContent content = revision.content();
        Instant wentLiveAt = revision.wentLiveAtOrNull();
        return new ListingPublicPreviewView(revision.revisionNo(), revision.status(), row.code().value(),
                row.transactionType().code(), unit == null ? null : unit.assetTypeOrNull(),
                unit == null ? null : unit.provinceCodeOrNull(), unit == null ? null : unit.provinceNameOrNull(),
                unit == null ? null : unit.wardCodeOrNull(), unit == null ? null : unit.wardNameOrNull(),
                unit == null ? null : unit.bedrooms(), unit == null ? null : unit.bathrooms(),
                unit == null ? null : unit.areaSqm(), revision.termsOrNull(), content.titleOrNull(),
                content.descriptionOrNull(), content.features(),
                wentLiveAt == null ? null : FreshnessPolicy.today(wentLiveAt),
                listings.findFreshnessSummary(row.listingId()).confirmedOnOrNull());
    }

    /** A malformed id reads nothing; an unknown listing answers the one "not found" (listing BR-5). */
    private ListingRow rowOf(String listingId) {
        if (listingId == null || !ID.matcher(listingId).matches()) {
            throw new ListingNotFoundException();
        }
        return listings.findRow(listingId).orElseThrow(ListingNotFoundException::new);
    }

    private static Optional<ListingRevision> revisionWith(Listing listing, RevisionStatus status) {
        return listing.revisions().stream().filter(revision -> revision.status() == status).findFirst();
    }

    /** The database holds a live revision's time with its status (a CHECK); none is a defect. */
    private static Instant wentLiveAt(ListingRevision live) {
        if (live.wentLiveAtOrNull() == null) {
            throw new IllegalStateException("the live revision has no time it went live");
        }
        return live.wentLiveAtOrNull();
    }

    /** An approval needs a terms snapshot that matches E05 (E06S4-D7): an Approved revision without one is a defect. */
    private static TermsSource termsSourceOf(ListingRevision approved) {
        if (approved.termsOrNull() == null) {
            throw new IllegalStateException("the approved revision has no terms snapshot");
        }
        return approved.termsOrNull().source();
    }

    /** The rule's answer; E05's groups only when the advertising right is one of the reasons (listing-publish BR-11). */
    private static ListingPublicationView.DisplayAnswer answerOf(List<DisplayReason> reasons,
                                                                 DisplayConditions conditions) {
        return new ListingPublicationView.DisplayAnswer(reasons.isEmpty(), reasons,
                reasons.contains(DisplayReason.ADVERTISING_RIGHT) ? conditions.agreementGroups() : List.of());
    }
}
