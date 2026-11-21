package vn.sunvibe.listing.core.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.application.command.RevisionFeaturesInput;
import vn.sunvibe.listing.core.application.command.RevisionSaveCommand;
import vn.sunvibe.listing.core.application.command.RevisionStartCommand;
import vn.sunvibe.listing.core.application.command.RevisionSubmitCommand;
import vn.sunvibe.listing.core.application.command.RevisionWithdrawCommand;
import vn.sunvibe.listing.core.application.command.TermsCaptureCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.ListingCreateResult;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfileFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfilePort;
import vn.sunvibe.listing.core.application.usecase.ListingCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.ChargeUnit;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.Furnishing;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.Pets;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.ShareMode;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;
import vn.sunvibe.listing.core.domain.policy.listing.ListingAccessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingCreatePolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the listing create (listing-publish E06-S1 spec 3.1) and the revision commands (E06-S2
 * spec 3.1 to 3.6). One read-write transaction each, with one {@code now}. The create reads E05's answer under its share
 * lock and writes the listing, its first revision and its history row. A revision command checks, in this order
 * (E06S2-D3): the listing exists and the agreement's standing names the caller as the assignee; the listing is not
 * archived; the data version; the revision; the status; and only then reads E05's terms or property's profile. No
 * revision command reads E05's right or takes its share lock. Nothing else: no outbox event, no row of another module,
 * no access log, no log of a value.
 */
@Service
@Transactional
public class ListingCommandService implements ListingCommandUseCase {

    /** listing-publish E06-S1 spec E06S1-D6: a taken code is skipped, five taken codes fail the command. */
    static final int CODE_DRAWS = 5;

    private static final Pattern ID = Pattern.compile("^[0-9A-Z]{26}$");

    private final ListingDomainRepository listings;
    private final AdvertisingRightPort rights;
    private final UnitProfilePort units;
    private final IdGenerator<String> ids;
    private final RandomGenerator random;

    /** The code is drawn from one SecureRandom this service owns (listing-detail BR-15; security.md). */
    @Autowired
    public ListingCommandService(ListingDomainRepository listings, AdvertisingRightPort rights, UnitProfilePort units,
                                 IdGenerator<String> ids) {
        this(listings, rights, units, ids, new SecureRandom());
    }

    ListingCommandService(ListingDomainRepository listings, AdvertisingRightPort rights, UnitProfilePort units,
                          IdGenerator<String> ids, RandomGenerator random) {
        this.listings = listings;
        this.rights = rights;
        this.units = units;
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

    @Override
    public RevisionCommandResult startRevision(RevisionStartCommand command, ListingViewer viewer) {
        Instant now = now();
        Listing listing = loadForDrafting(command.listingId(), null, command.dataVersion(), viewer);
        // listing-publish US-2 AC-6, BR-6: a new empty Draft only while no revision is open; created by the caller
        ListingRevision started = listing.startRevision(ids.generateId(), ids.generateId(), viewer.userId(), now);
        return written(listing, started);
    }

    @Override
    public RevisionCommandResult saveContent(RevisionSaveCommand command, ListingViewer viewer) {
        Instant now = now();
        Listing listing = loadForDrafting(command.listingId(), command.revisionNoOrNull(), command.dataVersion(),
                viewer);
        int revisionNo = targetOf(listing, command.revisionNoOrNull());
        RevisionContent content = new RevisionContent(command.titleOrNull(), command.descriptionOrNull(),
                featuresOf(command.features()));
        ListingRevision saved = listing.saveContent(revisionNo, content, ids.generateId(), viewer.userId(), now);
        return written(listing, saved);
    }

    @Override
    public RevisionCommandResult captureTerms(TermsCaptureCommand command, ListingViewer viewer) {
        Instant now = now();
        Listing listing = loadForDrafting(command.listingId(), command.revisionNoOrNull(), command.dataVersion(),
                viewer);
        int revisionNo = targetOf(listing, command.revisionNoOrNull());
        // the status first, so a locked revision is refused whatever E05 answers (plan E06S2-P6)
        listing.requireDraft(revisionNo);
        TermsSource source = TermsSource.valueOf(command.source());
        // listing-publish BR-4: a value copy of the version the Sale chose, read without a lock; no answer, an ended
        // agreement or a version E05 does not have now cannot be copied
        AgreementTermsFacts facts = rights.terms(listing.unitId(), listing.agreementId())
                .filter(found -> !found.ended())
                .orElseThrow(ListingRefusedException::termsSourceAbsent);
        AgreementTermsFacts.Terms chosen = source == TermsSource.IN_EFFECT ? facts.inEffectOrNull() : facts.pendingOrNull();
        if (chosen == null) {
            throw ListingRefusedException.termsSourceAbsent();
        }
        LocalDate pendingStartDateOrNull = source == TermsSource.PENDING_START ? facts.pendingStartDateOrNull() : null;
        RevisionTerms terms = snapshotOf(chosen, source, pendingStartDateOrNull, now);
        ListingRevision captured = listing.captureTerms(revisionNo, terms, ids.generateId(), viewer.userId(), now);
        return written(listing, captured);
    }

    @Override
    public RevisionCommandResult submit(RevisionSubmitCommand command, ListingViewer viewer) {
        Instant now = now();
        Listing listing = loadForDrafting(command.listingId(), command.revisionNoOrNull(), command.dataVersion(),
                viewer);
        int revisionNo = targetOf(listing, command.revisionNoOrNull());
        // listing-publish US-2 AC-4: every violation answers before property is read, so a broken profile cannot hide
        // one (plan E06S2-P6)
        listing.requireSubmittable(revisionNo);
        UnitSnapshot snapshot = snapshotOf(units.profile(listing.unitId())
                .orElseThrow(() -> new IllegalStateException("property answers no profile for the listing's unit")));
        ListingRevision submitted = listing.submit(revisionNo, snapshot, ids.generateId(), viewer.userId(), now);
        return written(listing, submitted);
    }

    @Override
    public RevisionCommandResult withdraw(RevisionWithdrawCommand command, ListingViewer viewer) {
        Instant now = now();
        Listing listing = loadForDrafting(command.listingId(), command.revisionNoOrNull(), command.dataVersion(),
                viewer);
        int revisionNo = targetOf(listing, command.revisionNoOrNull());
        ListingRevision withdrawn = listing.withdraw(revisionNo, ids.generateId(), viewer.userId(), now);
        return written(listing, withdrawn);
    }

    /**
     * E05's terms as a snapshot (listing-publish E06-S2 spec E06S2-D6, plan E06S2-P4): E05's codes mapped by explicit
     * switches and its values checked against E05's own bounds by {@link RevisionTerms}. An absent term, an unknown code
     * or a value outside the bounds is a broken contract: {@link IllegalStateException}, whose message names no value.
     */
    static RevisionTerms snapshotOf(AgreementTermsFacts.Terms terms, TermsSource source,
                                    LocalDate pendingStartDateOrNull, Instant capturedAt) {
        if (terms.monthlyRentOrNull() == null || terms.minimumTermMonthsOrNull() == null
                || terms.paymentPeriodMonthsOrNull() == null) {
            throw new IllegalStateException("E05's terms lack a rent, a minimum term or a payment period");
        }
        try {
            RevisionTerms.Deposit deposit = new RevisionTerms.Deposit(depositBasisOf(terms.depositBasisOrNull()),
                    terms.depositMonthsOrNull(), terms.depositAmountOrNull());
            List<RevisionTerms.Fee> fees = terms.fees().stream()
                    .map(fee -> new RevisionTerms.Fee(fee.name(), feeBasisOf(fee.basis()), fee.amountOrNull(),
                            chargeUnitOf(fee.chargeUnitOrNull())))
                    .toList();
            return new RevisionTerms(source, pendingStartDateOrNull, capturedAt, terms.monthlyRentOrNull(), deposit,
                    terms.minimumTermMonthsOrNull(), terms.paymentPeriodMonthsOrNull(), fees);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("E05's terms break listing's copy of their bounds", e);
        }
    }

    private static DepositBasis depositBasisOf(String code) {
        return switch (code == null ? "" : code) {
            case "MONTHS" -> DepositBasis.MONTHS;
            case "AMOUNT" -> DepositBasis.AMOUNT;
            default -> throw new IllegalStateException("E05 sent a deposit basis listing does not know");
        };
    }

    private static FeeBasis feeBasisOf(String code) {
        return switch (code == null ? "" : code) {
            case "AMOUNT" -> FeeBasis.AMOUNT;
            case "INCLUDED" -> FeeBasis.INCLUDED;
            case "PROVIDER_TARIFF" -> FeeBasis.PROVIDER_TARIFF;
            default -> throw new IllegalStateException("E05 sent a fee basis listing does not know");
        };
    }

    private static ChargeUnit chargeUnitOf(String codeOrNull) {
        if (codeOrNull == null) {
            return null;
        }
        return switch (codeOrNull) {
            case "PER_MONTH" -> ChargeUnit.PER_MONTH;
            case "PER_KWH" -> ChargeUnit.PER_KWH;
            case "PER_M3" -> ChargeUnit.PER_M3;
            case "PER_PERSON_PER_MONTH" -> ChargeUnit.PER_PERSON_PER_MONTH;
            case "PER_VEHICLE_PER_MONTH" -> ChargeUnit.PER_VEHICLE_PER_MONTH;
            case "ONE_OFF" -> ChargeUnit.ONE_OFF;
            default -> throw new IllegalStateException("E05 sent a charge unit listing does not know");
        };
    }

    /** property's profile as a snapshot (E06S2-D12); an asset type or a place off the contract fails closed. */
    private static UnitSnapshot snapshotOf(UnitProfileFacts profile) {
        try {
            return new UnitSnapshot(profile.assetTypeOrNull(), profile.provinceCodeOrNull(),
                    profile.provinceNameOrNull(), profile.wardCodeOrNull(), profile.wardNameOrNull(),
                    profile.bedrooms(), profile.bathrooms(), profile.areaSqm());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("property's unit profile breaks listing's copy of its contract", e);
        }
    }

    /**
     * The checks every revision command makes before its own (listing-publish E06-S2 spec E06S2-D3): a malformed id
     * reads nothing; the listing and its agreement's standing name the caller as the assignee (a standing E05 no longer
     * returns names nobody); not archived, stored or because the agreement ended; then the working set at the caller's
     * data version.
     */
    private Listing loadForDrafting(String listingId, Integer targetRevisionNoOrNull, long dataVersion,
                                    ListingViewer viewer) {
        if (listingId == null || !ID.matcher(listingId).matches()) {
            throw new ListingNotFoundException();
        }
        ListingRow row = listings.findRow(listingId).orElseThrow(ListingNotFoundException::new);
        // listing BR-16, listing-publish section 2: only the SALE or SALE_LEADER who holds the basis lead now drafts;
        // anyone else in those roles reads "not found" (listing BR-5)
        AgreementStandingFacts standing = rights.standings(List.of(row.agreementId())).stream()
                .filter(found -> row.agreementId().equals(found.agreementId()))
                .findFirst()
                .filter(found -> ListingAccessPolicy.mayDraft(viewer.expandedAuthorities(), viewer.userId(),
                        found.basisLeadAssigneeIdOrNull()))
                .orElseThrow(ListingNotFoundException::new);
        // listing-publish US-9 AC-3: an archived listing takes no command, its archive stored or effective
        if (row.storedState().effective(standing.ended()) == ListingState.ARCHIVED) {
            throw ListingRefusedException.archived();
        }
        Listing listing = listings.findForCommand(listingId, targetRevisionNoOrNull)
                .orElseThrow(ListingNotFoundException::new);
        // listing BR-3: a command acts on the version its caller read
        if (listing.rowVersion() != dataVersion) {
            throw new ListingChangedException();
        }
        return listing;
    }

    /** The revision named in the path, if the listing has it: an unknown or malformed number reads as not found. */
    private static int targetOf(Listing listing, Integer revisionNoOrNull) {
        if (revisionNoOrNull == null || listing.revision(revisionNoOrNull).isEmpty()) {
            throw new ListingNotFoundException();
        }
        return revisionNoOrNull;
    }

    /** The edge checked the codes, so an unknown one here is a defect, never a choice. */
    private static RevisionFeatures featuresOf(RevisionFeaturesInput input) {
        return new RevisionFeatures(
                input.furnishingOrNull() == null ? null : Furnishing.valueOf(input.furnishingOrNull()),
                input.airConditioner(), input.waterHeater(), input.fridge(),
                input.washingMachineOrNull() == null ? null : ShareMode.valueOf(input.washingMachineOrNull()),
                input.kitchenOrNull() == null ? null : ShareMode.valueOf(input.kitchenOrNull()),
                input.balcony(), input.elevator(), input.motorbikeParking(), input.carParking(), input.internet(),
                input.petsOrNull() == null ? null : Pets.valueOf(input.petsOrNull()),
                input.noSharedLandlord());
    }

    /** Writes the command and answers its revision with the listing's new data version (listing BR-3). */
    private RevisionCommandResult written(Listing listing, ListingRevision revision) {
        listings.update(listing);
        return new RevisionCommandResult(listing.id(), revision.revisionNo(), revision.status(),
                listing.rowVersion() + 1);
    }

    private static Instant now() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
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
