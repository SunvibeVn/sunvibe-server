package vn.sunvibe.listing.core.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.command.FreshnessRecordCommand;
import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.application.command.RevisionCopyCommand;
import vn.sunvibe.listing.core.application.command.RevisionFeaturesInput;
import vn.sunvibe.listing.core.application.command.RevisionSaveCommand;
import vn.sunvibe.listing.core.application.command.RevisionStartCommand;
import vn.sunvibe.listing.core.application.command.RevisionSubmitCommand;
import vn.sunvibe.listing.core.application.command.RevisionWithdrawCommand;
import vn.sunvibe.listing.core.application.command.TermsCaptureCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.FreshnessRecordResult;
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
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessNote;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Furnishing;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.Pets;
import vn.sunvibe.listing.core.domain.model.listing.PutLiveMoment;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.ShareMode;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingAccessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingCreatePolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.outbox.EventOutboxPort;
import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.time.AppClock;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the listing create (listing-publish E06-S1 spec 3.1) and the revision commands (E06-S2
 * spec 3.1 to 3.6). One read-write transaction each, with one {@code now}. The create reads E05's answer under its share
 * lock and writes the listing, its first revision and its history row. A revision command checks, in this order
 * (E06S2-D3): the listing exists and the agreement's standing names the caller as the assignee; the listing is not
 * archived; the data version; the revision; the status; and only then reads E05's terms or property's profile. No
 * revision command reads E05's right or takes its share lock. Nothing else: no outbox event, no row of another module,
 * no access log, no log of a value. A valid freshness record with an approved revision waiting tries the put live
 * (E06S6-D7): the listing row lock, E05's share lock and terms, property's availability, and, when the revision goes
 * live, the listing's state and the listing event.
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
    private final DisplayConditionsAssembler assembler;
    private final EventOutboxPort events;

    /** The code is drawn from one SecureRandom this service owns (listing-detail BR-15; security.md). */
    @Autowired
    public ListingCommandService(ListingDomainRepository listings, AdvertisingRightPort rights, UnitProfilePort units,
                                 IdGenerator<String> ids, DisplayConditionsAssembler assembler,
                                 EventOutboxPort events) {
        this(listings, rights, units, ids, new SecureRandom(), assembler, events);
    }

    ListingCommandService(ListingDomainRepository listings, AdvertisingRightPort rights, UnitProfilePort units,
                          IdGenerator<String> ids, RandomGenerator random, DisplayConditionsAssembler assembler,
                          EventOutboxPort events) {
        this.listings = listings;
        this.rights = rights;
        this.units = units;
        this.ids = ids;
        this.random = random;
        this.assembler = assembler;
        this.events = events;
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
     * listing-publish US-2 AC-6, BR-6 (E06-S4 spec 3.6, E06S4-D14): the revision commands' checks on the working set
     * with the source, then the source named in the path; the aggregate refuses a source not Needs changes, then an open
     * revision, and adds the copy by the caller.
     */
    @Override
    public RevisionCommandResult copyRevision(RevisionCopyCommand command, ListingViewer viewer) {
        Instant now = now();
        Listing listing = loadForDrafting(command.listingId(), command.revisionNoOrNull(), command.dataVersion(),
                viewer);
        int sourceNo = targetOf(listing, command.revisionNoOrNull());
        ListingRevision copy = listing.copyRevision(sourceNo, ids.generateId(), ids.generateId(), viewer.userId(),
                now);
        return written(listing, copy);
    }

    /**
     * listing-publish US-6 AC-1, AC-2, BR-9, BR-11 (E06-S3 spec 3.1, E06S3-D3): the scope and the archived check of
     * {@link #loadForRecording}, the data version, then the record dated today in Vietnam time, the summary only a valid
     * record moves forward, and one history row, written with the listing row's guarded update. A valid confirmation
     * tries to put the approved revision live (E06S6-D7), which may change the listing's state: then the record, the
     * listing's state and revisions go in the one guarded write, and the listing's event is appended. An invalid record,
     * or one with no approved revision waiting, reads no E05 right, terms or share lock and no unit.
     */
    @Override
    public FreshnessRecordResult recordFreshness(FreshnessRecordCommand command, ListingViewer viewer) {
        Instant now = now();
        ListingRow row = loadForRecording(command.listingId(), viewer);
        // listing BR-3: a command acts on the version its caller read
        if (row.rowVersion() != command.dataVersion()) {
            throw new ListingChangedException();
        }
        FreshnessSummary previous = listings.findFreshnessSummary(row.listingId());
        FreshnessNote note = FreshnessNote.ofOrNull(command.noteOrNull());
        long nextVersion = row.rowVersion() + 1;
        FreshnessRecord record = new FreshnessRecord(ids.generateId(), row.listingId(), nextVersion,
                FreshnessPolicy.today(now), now, viewer.userId(), command.channel(), command.outcome(),
                command.termsStillApplyOrNull(), command.unitUnchangedOrNull(), note == null ? null : note.text());
        // listing BR-3: the record leaves one history row; it holds no revision, outcome or note (E06S3-D9)
        ListingHistoryEntry entry = new ListingHistoryEntry(ids.generateId(), ListingHistoryAction.FRESHNESS_RECORDED,
                null, viewer.userId(), now);
        FreshnessSummary next = previous.after(record);
        Listing wentLive = record.valid() ? putLiveOnConfirmation(row, next, now).orElse(null) : null;
        if (wentLive == null) {
            listings.recordFreshness(row.listingId(), row.rowVersion(), now, next, record, entry);
        } else {
            listings.recordFreshnessWithPutLive(wentLive, row.rowVersion(), now, next, record, entry);
            // ADR-014 decision 3: the listing's event in the transaction of the write
            events.append(ListingChangedEvent.now(row.listingId()));
        }
        return new FreshnessRecordResult(row.listingId(), nextVersion, record.valid());
    }

    /**
     * listing-publish US-5 AC-3 moment 2, US-6 AC-4 (E06-S6 spec E06S6-D7): a valid record on an Unpublished or Published
     * listing with an Approved revision waiting tries the put live without actor, with the summary the record moves to.
     * The working set read after the row and the locked row must both still be on the version the caller read, the
     * listing row locked before E05's share lock on the agreement row (lock order: the listing row, then the agreement
     * row). Any display reason leaves the revision Approved and is not reported.
     *
     * @return the listing whose revision went live, to be written with the record; empty when nothing went live
     */
    private Optional<Listing> putLiveOnConfirmation(ListingRow row, FreshnessSummary next, Instant now) {
        if (row.storedState() != ListingState.UNPUBLISHED && row.storedState() != ListingState.PUBLISHED) {
            return Optional.empty();
        }
        Optional<Integer> approvedNo = listings.findApprovedRevisionNo(row.listingId());
        if (approvedNo.isEmpty()) {
            return Optional.empty();
        }
        Listing listing = listings.findForPutLive(row.listingId(), null).orElseThrow(ListingChangedException::new);
        if (listing.rowVersion() != row.rowVersion()) {
            throw new ListingChangedException();
        }
        long locked = listings.lockRow(row.listingId()).orElseThrow(ListingChangedException::new);
        if (locked != row.rowVersion()) {
            throw new ListingChangedException();
        }
        DisplayConditions conditions = assembler.underShareLock(row, row.storedState(), next, now);
        List<DisplayReason> reasons = listing.tryPutLive(approvedNo.get(), conditions, PutLiveMoment.VALID_CONFIRMATION,
                null, now, ids.generateId(), ids.generateId());
        return reasons.isEmpty() ? Optional.of(listing) : Optional.empty();
    }

    /**
     * The checks of the record before its own (listing-publish E06-S3 spec E06S3-D3, plan E06S3-P1): a malformed id
     * reads nothing; the listing and its agreement's standing name the caller as the assignee (a standing E05 no longer
     * returns names nobody); not archived, stored or because the agreement ended.
     */
    private ListingRow loadForRecording(String listingId, ListingViewer viewer) {
        if (listingId == null || !ID.matcher(listingId).matches()) {
            throw new ListingNotFoundException();
        }
        ListingRow row = listings.findRow(listingId).orElseThrow(ListingNotFoundException::new);
        // listing BR-16, listing-publish section 2: only the SALE or SALE_LEADER who holds the basis lead now records;
        // anyone else in those roles reads "not found" (listing BR-5)
        AgreementStandingFacts standing = rights.standings(List.of(row.agreementId())).stream()
                .filter(found -> row.agreementId().equals(found.agreementId()))
                .findFirst()
                .filter(found -> ListingAccessPolicy.mayRecordFreshness(viewer.expandedAuthorities(), viewer.userId(),
                        found.basisLeadAssigneeIdOrNull()))
                .orElseThrow(ListingNotFoundException::new);
        // listing-publish US-9 AC-3: an archived listing takes no command, its archive stored or effective
        if (row.storedState().effective(standing.ended()) == ListingState.ARCHIVED) {
            throw ListingRefusedException.archived();
        }
        return row;
    }

    /**
     * E05's terms in effect as the snapshot every terms comparison reads (listing-publish E06-S6 spec E06S6-D20): none
     * for none, else {@link #snapshotOf} with the source {@code IN_EFFECT}. The approval's terms check, the review read
     * and the put live's display facts call this one conversion, so they compare the same value.
     */
    static RevisionTerms inEffectSnapshotOrNull(AgreementTermsFacts.Terms termsOrNull, Instant now) {
        return termsOrNull == null ? null : snapshotOf(termsOrNull, TermsSource.IN_EFFECT, null, now);
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
