package vn.sunvibe.listing.core.testsupport;

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
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.ShareMode;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;

import java.time.Instant;
import java.util.List;

/** Revisions and their content for the listing-publish E06-S2 tests: fixed ids, times and values. */
public final class RevisionFixtures {

    public static final Instant CREATED = Instant.parse("2026-11-20T02:00:00.000001Z");
    public static final Instant NOW = Instant.parse("2026-11-20T03:15:00.123456Z");
    public static final ListingCode CODE = ListingCode.of("TD-7K3M-Q9TX");
    /** 30 code points that break no rule. */
    public static final String TITLE = "Phòng trọ mới gần chợ Hải Châu";
    /** About 140 code points that break no rule. */
    public static final String DESCRIPTION = ("Phòng sạch sẽ, có cửa sổ lớn và"
            + " ban công rộng.").repeat(3);
    public static final RevisionFeatures EVERY_FEATURE = new RevisionFeatures(Furnishing.FULL, true, true, true,
            ShareMode.PRIVATE, ShareMode.SHARED, true, true, true, true, true, Pets.NOT_ALLOWED, true);

    private RevisionFixtures() {
    }

    public static RevisionContent completeContent() {
        return new RevisionContent(TITLE, DESCRIPTION, EVERY_FEATURE);
    }

    public static RevisionTerms inEffectTerms() {
        return new RevisionTerms(TermsSource.IN_EFFECT, null, NOW, 8_000_000L,
                new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1,
                List.of(new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                        new RevisionTerms.Fee("Internet", FeeBasis.INCLUDED, null, null),
                        new RevisionTerms.Fee("Nước", FeeBasis.PROVIDER_TARIFF, null, null)));
    }

    public static UnitSnapshot unitSnapshot() {
        return new UnitSnapshot("APARTMENT", "48", "Đà Nẵng", "20194", "Phường Hải Châu",
                2, 1, 32.5);
    }

    public static ListingRevision draft(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.DRAFT, "S1", CREATED, CREATED,
                RevisionContent.EMPTY, null, null, null, null);
    }

    /** A Draft with a complete content and an in-effect terms snapshot: it may be submitted. */
    public static ListingRevision completeDraft(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.DRAFT, "S1", CREATED, CREATED,
                completeContent(), inEffectTerms(), null, null, null);
    }

    public static ListingRevision pending(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.PENDING_REVIEW, "S1", CREATED, CREATED,
                completeContent(), inEffectTerms(), unitSnapshot(), CREATED, "S1");
    }

    public static ListingRevision withStatus(int revisionNo, RevisionStatus status) {
        return ListingRevision.load("R" + revisionNo, revisionNo, status, "S1", CREATED, CREATED,
                RevisionContent.EMPTY, null, null, null, null);
    }

    /** A listing loaded for a command, row version 7, with the given greatest number and working set. */
    public static Listing forCommand(int newestRevisionNo, ListingRevision... workingSet) {
        return Listing.loadForCommand("L1", CODE, "U1", TransactionType.LONG_TERM_RENT, "A1", ListingState.UNPUBLISHED,
                "S1", CREATED, CREATED, 7L, newestRevisionNo, List.of(workingSet));
    }
}
