package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;

/**
 * The publication of a listing's live revision (listing-publish E06-S7 spec E06S7-D2, D3, D11; criterion PB-10, its unit
 * part): the one builder copies the public data of the live revision and the internal ids, gives its terms the source
 * {@code IN_EFFECT} taken at the put-live time, encodes the terms with length prefixes into a SHA-256 digest, and, for a
 * revision submitted before the fee-name check, checks each fee name once and builds nothing when one fails. Its
 * {@code toString} names no value.
 */
class ListingPublicationTest {

    /** E06S7-D2's test vector, the fee name written with escapes: "Điện" is U+0110, i, U+1EC7, n (7 bytes). */
    private static final String VECTOR = "2:v17:80000006:MONTHS1:2-1:61:11:11:17:Điện6:AMOUNT4:35007:PER_KWH";
    private static final String PHONE_FEE_NAME = "Gửi xe, gọi 0905 123 456";

    /** E06S7-D2, plan E06S7-P5: the vector's bytes, and its digest computed here, independent of the class. */
    @Test
    void canonicalTerms_testVector_bytesAndIndependentDigest() throws NoSuchAlgorithmException {
        RevisionTerms terms = terms(new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null),
                List.of(new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH)));
        String expectedDigest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(VECTOR.getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertEquals(69, VECTOR.getBytes(StandardCharsets.UTF_8).length),
                () -> assertEquals(VECTOR, ListingPublication.canonicalTerms(terms)),
                () -> assertEquals(expectedDigest, ListingPublication.digestOf(terms)),
                () -> assertEquals(64, ListingPublication.digestOf(terms).length()),
                () -> assertEquals(ListingPublication.digestOf(terms).toLowerCase(java.util.Locale.ROOT),
                        ListingPublication.digestOf(terms)));
    }

    /** E06S7-D2: the fee count, each fee's position, an absent value as "-", codes by their names. */
    @Test
    void canonicalTerms_amountDepositAndFeesWithoutAmount_countPositionsAbsentValuesCodes() {
        RevisionTerms terms = terms(new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 5_000_000L),
                List.of(new RevisionTerms.Fee("Internet", FeeBasis.INCLUDED, null, null),
                        new RevisionTerms.Fee("Nước", FeeBasis.PROVIDER_TARIFF, null, null)));

        assertEquals("2:v17:80000006:AMOUNT-7:50000001:61:11:21:18:Internet8:INCLUDED--1:27:Nước15:PROVIDER_TARIFF--",
                ListingPublication.canonicalTerms(terms));
    }

    /** Review Focus 3: a name holding the separators cannot join into another fee list's text. */
    @Test
    void digestOf_namesWhoseNaiveJoinIsTheSame_differ() {
        RevisionTerms joined = terms(new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null),
                List.of(new RevisionTerms.Fee("A1:B", FeeBasis.INCLUDED, null, null)));
        RevisionTerms apart = terms(new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null),
                List.of(new RevisionTerms.Fee("A", FeeBasis.INCLUDED, null, null),
                        new RevisionTerms.Fee("B", FeeBasis.INCLUDED, null, null)));

        assertNotEquals(ListingPublication.canonicalTerms(joined), ListingPublication.canonicalTerms(apart));
        assertNotEquals(ListingPublication.digestOf(joined), ListingPublication.digestOf(apart));
    }

    /** E06S7-D2, D3: the public data of the live revision, the internal ids, terms in effect at the put-live time. */
    @Test
    void of_liveRevision_publicDataIdsAndTermsAtWentLive() {
        ListingRevision live = live(RevisionFixtures.inEffectTerms(), true);

        ListingPublication publication = ListingPublication.of(listing(live), live).orElseThrow();

        RevisionTerms snapshot = RevisionFixtures.inEffectTerms();
        assertAll(
                () -> assertEquals(List.of("L1", CODE.value(), "U1", "A1", "LONG_TERM_RENT"), List.of(
                        publication.listingId(), publication.code(), publication.unitId(), publication.agreementId(),
                        publication.transactionType())),
                () -> assertEquals(List.of("APARTMENT", "48", "20194"), List.of(publication.propertyTypeOrNull(),
                        publication.provinceCodeOrNull(), publication.wardCodeOrNull())),
                () -> assertEquals(List.of(2, 1), List.of(publication.bedrooms(), publication.bathrooms())),
                () -> assertEquals(32.5, publication.areaSqm()),
                () -> assertEquals(List.of(RevisionFixtures.TITLE, RevisionFixtures.DESCRIPTION),
                        List.of(publication.title(), publication.description())),
                () -> assertEquals(RevisionFixtures.EVERY_FEATURE, publication.features()),
                () -> assertEquals(NOW, publication.wentLiveAt()),
                () -> assertEquals(new RevisionTerms(TermsSource.IN_EFFECT, null, NOW, snapshot.monthlyRent(),
                        snapshot.deposit(), snapshot.minimumTermMonths(), snapshot.paymentPeriodMonths(),
                        snapshot.fees()), publication.terms()),
                () -> assertEquals(ListingPublication.digestOf(snapshot), publication.termsDigest()));
    }

    /** E06S7-D11: a revision submitted before the check, its fee names clean: checked once, built. */
    @Test
    void of_flagFalseCleanNames_builds() {
        ListingRevision live = live(RevisionFixtures.inEffectTerms(), false);

        assertTrue(ListingPublication.of(listing(live), live).isPresent());
    }

    /** E06S7-D11: a revision submitted before the check with a fee name that breaks a rule: no publication. */
    @Test
    void of_flagFalseBrokenName_empty() {
        ListingRevision live = live(withFee(PHONE_FEE_NAME), false);

        assertEquals(Optional.empty(), ListingPublication.of(listing(live), live));
    }

    /** LP-15: a revision whose names passed at submission is never checked again. */
    @Test
    void of_flagTrueBrokenName_builds() {
        ListingRevision live = live(withFee(PHONE_FEE_NAME), true);

        assertTrue(ListingPublication.of(listing(live), live).isPresent());
    }

    /** A revision that is not Live, or a Live one without its terms or unit snapshot, is a defect. */
    @Test
    void of_notLiveOrWithoutSnapshot_illegalState() {
        ListingRevision approved = ListingRevision.load("R1", 1, RevisionStatus.APPROVED, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, "S1", null, null, null, true);
        ListingRevision noUnit = ListingRevision.load("R1", 1, RevisionStatus.LIVE, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), null, CREATED, "S1", null, NOW,
                null, true);
        ListingRevision noTerms = ListingRevision.load("R1", 1, RevisionStatus.LIVE, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), null, RevisionFixtures.unitSnapshot(), CREATED, "S1", null, NOW,
                null, true);
        ListingRevision header = ListingRevision.load("R1", 1, RevisionStatus.LIVE, "S1", CREATED, CREATED, NOW, null);

        for (ListingRevision revision : List.of(approved, noUnit, noTerms, header)) {
            assertThrows(IllegalStateException.class, () -> ListingPublication.of(listing(live(
                    RevisionFixtures.inEffectTerms(), true)), revision));
        }
    }

    @Test
    void toString_namesNoValue() {
        ListingRevision live = live(RevisionFixtures.inEffectTerms(), true);

        String text = ListingPublication.of(listing(live), live).orElseThrow().toString();

        assertAll(
                () -> assertFalse(text.contains(CODE.value()), text),
                () -> assertFalse(text.contains(RevisionFixtures.TITLE), text),
                () -> assertFalse(text.contains("Điện"), text),
                () -> assertFalse(text.contains("8000000"), text),
                () -> assertFalse(text.contains("U1"), text));
    }

    private static Listing listing(ListingRevision live) {
        return Listing.loadForCommand("L1", CODE, "U1", TransactionType.LONG_TERM_RENT, "A1", ListingState.PUBLISHED, "S1",
                CREATED, CREATED, 7L, 1, List.of(live));
    }

    private static ListingRevision live(RevisionTerms terms, boolean feeNamesChecked) {
        return ListingRevision.load("R1", 1, RevisionStatus.LIVE, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), terms, RevisionFixtures.unitSnapshot(), CREATED, "S1", null, NOW,
                null, feeNamesChecked);
    }

    private static RevisionTerms withFee(String name) {
        return terms(new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null),
                List.of(new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                        new RevisionTerms.Fee(name, FeeBasis.INCLUDED, null, null)));
    }

    private static RevisionTerms terms(RevisionTerms.Deposit deposit, List<RevisionTerms.Fee> fees) {
        return new RevisionTerms(TermsSource.IN_EFFECT, null, Instant.parse("2026-11-20T03:15:00.123456Z"), 8_000_000L,
                deposit, 6, 1, fees);
    }
}
