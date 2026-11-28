package vn.sunvibe.listing.core.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ListingPublicView;
import vn.sunvibe.listing.core.application.usecase.ListingPublicQueryUseCase;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.ListingPublication;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingDisplayPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Use Case Implementation - the anonymous answer about a listing (listing-publish E06-S7 spec E06S7-D5; listing BR-4,
 * BR-5, BR-6; listing-publish BR-11, BR-14; ADR-012 decisions 2, 4). One read-only transaction at {@code REPEATABLE_READ},
 * so the publication row, the listing row, the freshness summary and the answers of E05 and property, whose reads join
 * it (ADR-009), come from one snapshot; one clock reading; no lock (ADR-012 decision 2: the public reads never lock).
 * Every hidden case is the one "not found"; a fact listing cannot read is not caught, so the answer fails closed
 * (E06S1-D9). No log line: nothing of the request, its code included, is written.
 */
@Service
@Transactional(readOnly = true)
public class ListingPublicQueryService implements ListingPublicQueryUseCase {

    private final ListingDomainRepository listings;
    private final DisplayConditionsAssembler assembler;

    public ListingPublicQueryService(ListingDomainRepository listings, DisplayConditionsAssembler assembler) {
        this.listings = listings;
        this.assembler = assembler;
    }

    /**
     * The steps of E06S7-D5, the first that fails answering the one "not found": (1) the publication row of the code;
     * (2) the listing's row, stored Published (a row whose listing a later command left in another state never shows);
     * (3) E06-S6's display facts read without a lock and the rule of a shown revision over the row's terms, no reason.
     * The confirmation date is the freshness summary's, present because condition (e) held.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ListingPublicView publicListing(String code) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        ListingPublication publication = listings.findPublication(code).orElseThrow(ListingNotFoundException::new);
        ListingRow row = listings.findRow(publication.listingId()).orElseThrow(ListingNotFoundException::new);
        if (row.storedState() != ListingState.PUBLISHED) {
            throw new ListingNotFoundException();
        }
        DisplayConditions conditions = assembler.withoutLock(row, ListingState.PUBLISHED, now);
        if (!ListingDisplayPolicy.liveReasons(conditions, publication.terms(), now).isEmpty()) {
            throw new ListingNotFoundException();
        }
        LocalDate lastConfirmedOn = conditions.freshness().confirmedOnOrNull();
        if (lastConfirmedOn == null) {
            throw new IllegalStateException("a shown listing has a valid confirmation");
        }
        return new ListingPublicView(publication.code(), publication.transactionType(),
                publication.propertyTypeOrNull(), publication.provinceCodeOrNull(), publication.wardCodeOrNull(),
                publication.bedrooms(), publication.bathrooms(), publication.areaSqm(), publication.terms(),
                publication.title(), publication.description(), publication.features(),
                FreshnessPolicy.today(publication.wentLiveAt()), lastConfirmedOn);
    }
}
