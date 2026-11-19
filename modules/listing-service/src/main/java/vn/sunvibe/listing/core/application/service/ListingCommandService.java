package vn.sunvibe.listing.core.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.ListingCreateResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.usecase.ListingCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.ListingAccessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingCreatePolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Use Case Implementation - the listing create (listing-publish E06-S1 spec 3.1). One read-write transaction: the
 * agreement's standing authorizes the caller, E05's answer is read under its share lock, and the listing, its first
 * revision and its history row are written together. Nothing else: no outbox event, no row of another module, no
 * access log, no log of a value (E06S1-D16).
 */
@Service
@Transactional
public class ListingCommandService implements ListingCommandUseCase {

    /** listing-publish E06-S1 spec E06S1-D6: a taken code is skipped, five taken codes fail the command. */
    static final int CODE_DRAWS = 5;

    private final ListingDomainRepository listings;
    private final AdvertisingRightPort rights;
    private final IdGenerator<String> ids;
    private final RandomGenerator random;

    /** The code is drawn from one SecureRandom this service owns (listing-detail BR-15; security.md). */
    @Autowired
    public ListingCommandService(ListingDomainRepository listings, AdvertisingRightPort rights,
                                 IdGenerator<String> ids) {
        this(listings, rights, ids, new SecureRandom());
    }

    ListingCommandService(ListingDomainRepository listings, AdvertisingRightPort rights, IdGenerator<String> ids,
                          RandomGenerator random) {
        this.listings = listings;
        this.rights = rights;
        this.ids = ids;
        this.random = random;
    }

    @Override
    public ListingCreateResult create(ListingCreateCommand command, ListingViewer viewer) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        String agreementId = command.agreementId();
        // listing BR-16, listing-publish US-1 AC-3: only a SALE or SALE_LEADER who holds the agreement's basis lead now
        // creates; an unknown agreement, another holder and another transaction type answer the same "not found"
        // (listing BR-5), read before anything else so nothing about the agreement is told
        AgreementStandingFacts standing = rights.standings(List.of(agreementId)).stream()
                .filter(found -> agreementId.equals(found.agreementId()))
                .findFirst()
                .filter(found -> ListingAccessPolicy.mayCreate(viewer.expandedAuthorities(), viewer.userId(),
                        found.basisLeadAssigneeIdOrNull()))
                .filter(found -> TransactionType.LONG_TERM_RENT.code().equals(found.transactionType()))
                .orElseThrow(ListingNotFoundException::new);
        if (standing.ended()) {
            throw ListingRefusedException.notAllowed(ListingCreatePolicy.refusals(true, null));
        }
        // listing BR-1: at most one listing not archived per agreement; the unique index decides a race below
        if (listings.hasOpen(agreementId)) {
            throw ListingRefusedException.exists();
        }
        // listing-publish BR-1, LP-1 and ADR-012 decision 2 "Commands": E05's answer read under FOR SHARE on the
        // agreement row, so a stop committed first refuses the create and one that starts later waits for its commit
        ListingCreatePolicy.AnswerFacts answer = rights.answerUnderShareLock(standing.unitId(), agreementId)
                .map(ListingCommandService::toDomain)
                .orElse(null);
        List<AgreementReasonGroup> refusals = ListingCreatePolicy.refusals(false, answer);
        if (!refusals.isEmpty()) {
            throw ListingRefusedException.notAllowed(refusals);
        }
        ListingCode code = drawFreeCode();
        // listing BR-1: the unit and type come from the agreement's standing, bound for life; listing BR-3: history
        // from the first command
        Listing listing = Listing.create(ids.generateId(), code, standing.unitId(), agreementId, viewer.userId(), now,
                ids.generateId(), ids.generateId());
        try {
            listings.insert(listing);
        } catch (OpenListingExistsException e) {
            throw ListingRefusedException.exists();
        }
        return new ListingCreateResult(listing.id(), code.value());
    }

    /** listing-detail BR-15: a code never reissued; a code another listing holds is skipped. */
    private ListingCode drawFreeCode() {
        for (int draw = 0; draw < CODE_DRAWS; draw++) {
            ListingCode code = ListingCode.draw(random);
            if (!listings.existsByCode(code.value())) {
                return code;
            }
        }
        throw new IllegalStateException("no free listing code after " + CODE_DRAWS + " draws");
    }

    /** E05's codes as listing's domain values; a code listing does not know fails (E06S1-D9), never read as allowed. */
    private static ListingCreatePolicy.AnswerFacts toDomain(AdvertisingAnswerFacts facts) {
        return new ListingCreatePolicy.AnswerFacts(facts.ended(), facts.allowed(),
                facts.reasonGroups().stream().map(AgreementReasonGroup::ofCode).toList());
    }
}
