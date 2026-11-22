package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;

/**
 * The parts of a revision that differ from the live revision (listing-publish US-4 AC-2; E06-S6 spec E06S6-D12; PL-12
 * domain half): the title and the description as stored texts, the thirteen features, and the terms by value only, in
 * that fixed order; and the two revision times set only through their transitions (E06S6-D6 "Times").
 */
class ListingRevisionChangedPartsTest {

    private static final RevisionFeatures OTHER_FEATURES = new RevisionFeatures(Furnishing.BASIC, true, true, true,
            ShareMode.PRIVATE, ShareMode.SHARED, true, true, true, true, true, Pets.NOT_ALLOWED, true);

    @Test
    void changedPartsFrom_titleOnly_title() {
        ListingRevision asked = revision(new RevisionContent("Tiêu đề khác hẳn", RevisionFixtures.DESCRIPTION,
                RevisionFixtures.EVERY_FEATURE), RevisionFixtures.inEffectTerms());

        assertEquals(List.of(ComparedPart.TITLE), asked.changedPartsFrom(live()));
    }

    @Test
    void changedPartsFrom_featuresOnly_features() {
        ListingRevision asked = revision(new RevisionContent(RevisionFixtures.TITLE, RevisionFixtures.DESCRIPTION,
                OTHER_FEATURES), RevisionFixtures.inEffectTerms());

        assertEquals(List.of(ComparedPart.FEATURES), asked.changedPartsFrom(live()));
    }

    @Test
    void changedPartsFrom_feeChanged_terms() {
        RevisionTerms terms = RevisionFixtures.inEffectTerms();
        RevisionTerms feeChanged = new RevisionTerms(terms.source(), terms.pendingStartDateOrNull(), terms.capturedAt(),
                terms.monthlyRent(), terms.deposit(), terms.minimumTermMonths(), terms.paymentPeriodMonths(),
                List.of(new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_600L, ChargeUnit.PER_KWH),
                        terms.fees().get(1), terms.fees().get(2)));

        assertEquals(List.of(ComparedPart.TERMS),
                revision(RevisionFixtures.completeContent(), feeChanged).changedPartsFrom(live()));
    }

    @Test
    void changedPartsFrom_sameValuesOtherSourceAndCaptureTime_none() {
        RevisionTerms terms = RevisionFixtures.inEffectTerms();
        RevisionTerms pendingCopy = new RevisionTerms(TermsSource.PENDING_START, LocalDate.of(2026, 12, 1),
                NOW.plusSeconds(3_600), terms.monthlyRent(), terms.deposit(), terms.minimumTermMonths(),
                terms.paymentPeriodMonths(), terms.fees());

        assertEquals(List.of(), revision(RevisionFixtures.completeContent(), pendingCopy).changedPartsFrom(live()));
    }

    @Test
    void changedPartsFrom_askedWithoutSnapshot_terms() {
        assertEquals(List.of(ComparedPart.TERMS),
                revision(RevisionFixtures.completeContent(), null).changedPartsFrom(live()));
    }

    @Test
    void changedPartsFrom_everyPart_fixedOrder() {
        ListingRevision asked = revision(new RevisionContent("Tiêu đề khác hẳn", null, OTHER_FEATURES), null);

        assertEquals(List.of(ComparedPart.TITLE, ComparedPart.DESCRIPTION, ComparedPart.FEATURES, ComparedPart.TERMS),
                asked.changedPartsFrom(live()));
    }

    @Test
    void wentLiveAndReplaced_times_answeredAndStatusGuarded() {
        Instant later = NOW.plusSeconds(60);
        ListingRevision approved = withStatus(RevisionStatus.APPROVED);

        ListingRevision wentLive = approved.wentLive(NOW);
        ListingRevision replaced = wentLive.replaced(later);

        assertAll(
                () -> assertEquals(RevisionStatus.LIVE, wentLive.status()),
                () -> assertEquals(NOW, wentLive.wentLiveAtOrNull()),
                () -> assertNull(wentLive.replacedAtOrNull()),
                () -> assertEquals(NOW, wentLive.updatedAt()),
                () -> assertEquals(RevisionStatus.REPLACED, replaced.status()),
                () -> assertEquals(NOW, replaced.wentLiveAtOrNull()),
                () -> assertEquals(later, replaced.replacedAtOrNull()),
                () -> assertEquals(later, replaced.updatedAt()),
                () -> assertEquals(approved.content(), replaced.content()),
                () -> assertEquals(approved.termsOrNull(), replaced.termsOrNull()));
        for (RevisionStatus status : RevisionStatus.values()) {
            ListingRevision revision = withStatus(status);
            if (status != RevisionStatus.APPROVED) {
                assertThrows(IllegalStateException.class, () -> revision.wentLive(NOW), "wentLive " + status);
            }
            if (status != RevisionStatus.LIVE) {
                assertThrows(IllegalStateException.class, () -> revision.replaced(NOW), "replaced " + status);
            }
        }
    }

    /** The live revision: the complete content and the in-effect terms. */
    private static ListingRevision live() {
        return ListingRevision.load("R1", 1, RevisionStatus.LIVE, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, "S1", null, CREATED, null);
    }

    private static ListingRevision revision(RevisionContent content, RevisionTerms termsOrNull) {
        return ListingRevision.load("R2", 2, RevisionStatus.DRAFT, "S1", CREATED, CREATED, content, termsOrNull, null,
                null, null);
    }

    private static ListingRevision withStatus(RevisionStatus status) {
        return ListingRevision.load("R2", 2, status, "S1", CREATED, CREATED, RevisionFixtures.completeContent(),
                RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(), CREATED, "S1");
    }
}
