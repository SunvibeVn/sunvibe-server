package vn.sunvibe.listing.core.domain.model.listing;

import vn.sunvibe.listing.core.domain.policy.listing.ListingTextRules;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * The publication of a listing (listing-publish E06-S7 spec E06S7-D2, D3; ADR-012 decision 1; listing BR-6,
 * listing-publish BR-14): the public data of its live revision, exactly the groups this slice serves (the code, the
 * transaction type, the unit snapshot's type, place codes, rooms and area, the terms snapshot with its fees, title,
 * description, features, the put-live time), and the internal values the row keeps beside them (the listing, its unit and
 * agreement, the digest of the terms). Built only by {@link #of}, the one builder, when a put live succeeds; written in
 * the put live's guarded write. Its terms have the source {@code IN_EFFECT} taken at the put-live time: they equal by
 * value E05's terms in effect then, and the terms match rule compares values only (E06S7-D5). {@link #toString()} names
 * no value.
 */
public record ListingPublication(String listingId, String code, String unitId, String agreementId,
                                 String transactionType, String propertyTypeOrNull, String provinceCodeOrNull,
                                 String wardCodeOrNull, int bedrooms, int bathrooms, double areaSqm,
                                 RevisionTerms terms, String title, String description, RevisionFeatures features,
                                 Instant wentLiveAt, String termsDigest) {

    /** E06S7-D2: the version mark that opens the canonical string of the terms. */
    private static final String VERSION_MARK = "v1";
    /** E06S7-D2: an absent value is this single character, without a length. */
    private static final String ABSENT = "-";
    private static final int DIGEST_LENGTH = 64;

    public ListingPublication {
        if (listingId == null || code == null || unitId == null || agreementId == null || transactionType == null
                || terms == null || title == null || description == null || features == null || wentLiveAt == null
                || termsDigest == null || termsDigest.length() != DIGEST_LENGTH) {
            throw new IllegalArgumentException("a publication holds every public value, its ids and its digest");
        }
        if ((provinceCodeOrNull == null) != (wardCodeOrNull == null)) {
            throw new IllegalArgumentException("a publication's province and ward come together");
        }
    }

    /**
     * The publication of {@code live}, the listing's Live revision in its full form with its terms and unit snapshot (a
     * missing one is a defect). For a revision submitted before the fee-name check (its flag false), each fee name is
     * checked once with the machine rules of listing-publish BR-2, and a name that breaks one gives no publication
     * (E06S7-D11: the listing then shows nothing until a revision whose names pass goes live). A revision whose names
     * passed at its submission is not checked again (LP-15).
     */
    public static Optional<ListingPublication> of(Listing listing, ListingRevision live) {
        if (live.status() != RevisionStatus.LIVE || !live.fullForm()) {
            throw new IllegalStateException("a publication is built from the live revision in its full form");
        }
        RevisionTerms snapshot = live.termsOrNull();
        UnitSnapshot unit = live.unitSnapshotOrNull();
        Instant wentLiveAt = live.wentLiveAtOrNull();
        if (snapshot == null || unit == null || wentLiveAt == null || live.content().titleOrNull() == null
                || live.content().descriptionOrNull() == null) {
            throw new IllegalStateException("a live revision holds its content, its snapshots and its put-live time");
        }
        if (!live.feeNamesChecked() && snapshot.fees().stream()
                .anyMatch(fee -> !ListingTextRules.broken(fee.name()).isEmpty())) {
            return Optional.empty();
        }
        RevisionTerms terms = new RevisionTerms(TermsSource.IN_EFFECT, null, wentLiveAt, snapshot.monthlyRent(),
                snapshot.deposit(), snapshot.minimumTermMonths(), snapshot.paymentPeriodMonths(), snapshot.fees());
        return Optional.of(new ListingPublication(listing.id(), listing.code().value(), listing.unitId(),
                listing.agreementId(), listing.transactionType().name(), unit.assetTypeOrNull(),
                unit.provinceCodeOrNull(), unit.wardCodeOrNull(), unit.bedrooms(), unit.bathrooms(), unit.areaSqm(),
                terms, live.content().titleOrNull(), live.content().descriptionOrNull(), live.content().features(),
                wentLiveAt, digestOf(terms)));
    }

    /**
     * E06S7-D2: the canonical string of the terms. Fields in this order: the version mark, the monthly rent, the deposit
     * basis, months and amount, the minimum term, the payment period, the number of fees, then per fee in E05's order its
     * 1-based position, name, basis, amount and charge unit. Each field is its length in UTF-8 bytes in decimal, a colon
     * and its UTF-8 text; an absent value is "-". Numbers are decimal, codes their names, names as stored. The lengths
     * make the string unambiguous whatever a name holds.
     */
    static String canonicalTerms(RevisionTerms terms) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, VERSION_MARK);
        field(canonical, Long.toString(terms.monthlyRent()));
        field(canonical, terms.deposit().basis().name());
        field(canonical, terms.deposit().monthsOrNull() == null ? null : terms.deposit().monthsOrNull().toString());
        field(canonical, terms.deposit().amountOrNull() == null ? null : terms.deposit().amountOrNull().toString());
        field(canonical, Integer.toString(terms.minimumTermMonths()));
        field(canonical, Integer.toString(terms.paymentPeriodMonths()));
        List<RevisionTerms.Fee> fees = terms.fees();
        field(canonical, Integer.toString(fees.size()));
        for (int index = 0; index < fees.size(); index++) {
            RevisionTerms.Fee fee = fees.get(index);
            field(canonical, Integer.toString(index + 1));
            field(canonical, fee.name());
            field(canonical, fee.basis().name());
            field(canonical, fee.amountOrNull() == null ? null : fee.amountOrNull().toString());
            field(canonical, fee.chargeUnitOrNull() == null ? null : fee.chargeUnitOrNull().name());
        }
        return canonical.toString();
    }

    /** E06S7-D2: the lowercase hexadecimal SHA-256 of the canonical string's UTF-8 bytes. */
    static String digestOf(RevisionTerms terms) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalTerms(terms).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform provides SHA-256", e);
        }
    }

    private static void field(StringBuilder canonical, String valueOrNull) {
        if (valueOrNull == null) {
            canonical.append(ABSENT);
            return;
        }
        canonical.append(valueOrNull.getBytes(StandardCharsets.UTF_8).length).append(':').append(valueOrNull);
    }

    /** Names no value: the public data is the listing's, and the ids and terms are internal. */
    @Override
    public String toString() {
        return "ListingPublication[hidden]";
    }
}
