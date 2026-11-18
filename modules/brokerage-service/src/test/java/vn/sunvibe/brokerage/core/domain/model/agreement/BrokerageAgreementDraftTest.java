package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The draft of an agreement and its edit (brokerage-agreement BR-5, BR-8, BR-9; E05-S3 spec S3-D2, S3-D6, 5.1): the
 * draft is the pending version of a DRAFTING agreement while it is DRAFT, and an edit replaces its content and both
 * update times, nothing else.
 */
class BrokerageAgreementDraftTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-11-21T03:15:00Z");
    private static final Instant NOW = Instant.parse("2026-11-22T08:30:00.654321Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final AgreementPeriod NEW_PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-02-01"), LocalDate.parse("2028-01-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, true);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1,
            List.of(new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH)));

    @Test
    void draft_draftingWithDraftVersion_present() {
        AgreementVersion rejected = version("V1", 1, VersionStatus.OWNER_REJECTED);
        AgreementVersion draft = version("V2", 2, VersionStatus.DRAFT);

        BrokerageAgreement agreement = load(AgreementStatus.DRAFTING, List.of(rejected, draft));

        assertEquals(Optional.of(draft), agreement.draft());
    }

    @Test
    void draft_otherStatesOrVersions_empty() {
        assertEquals(Optional.empty(), load(AgreementStatus.CANCELLED,
                List.of(version("V1", 1, VersionStatus.CANCELLED))).draft());
        assertEquals(Optional.empty(), load(AgreementStatus.DRAFTING,
                List.of(version("V1", 1, VersionStatus.AWAITING_OWNER))).draft());
        assertEquals(Optional.empty(), load(AgreementStatus.AWAITING_CONFIRMATION,
                List.of(version("V1", 1, VersionStatus.AWAITING_OWNER))).draft());
        assertEquals(Optional.empty(), load(AgreementStatus.ACTIVE,
                List.of(version("V1", 1, VersionStatus.EFFECTIVE), version("V2", 2, VersionStatus.DRAFT))).draft());
    }

    @Test
    void editDraft_replacesContentAndTimesKeepsTheRest() {
        AgreementVersion rejected = version("V1", 1, VersionStatus.OWNER_REJECTED);
        AgreementVersion draft = version("V2", 2, VersionStatus.DRAFT);
        BrokerageAgreement agreement = load(AgreementStatus.DRAFTING, List.of(rejected, draft));

        BrokerageAgreement edited = agreement.editDraft(NEW_PERIOD, CONSENT, TERMS, BrokerageFee.NONE, NOW);

        assertEquals("A1", edited.getId());
        assertEquals("U1", edited.getUnitId());
        assertEquals("P101", edited.getUnitCodeOrNull());
        assertEquals("P1", edited.getPropertyId());
        assertEquals(TransactionType.LONG_TERM_RENT, edited.getTransactionType());
        assertEquals("O1", edited.getOwnerUserId());
        assertEquals("L1", edited.getBasisLeadId());
        assertEquals(AgreementStatus.DRAFTING, edited.getStatus());
        assertEquals("S1", edited.getCreatedByUserId());
        assertEquals(CREATED, edited.getCreatedAt());
        assertEquals(NOW, edited.getUpdatedAt());
        assertEquals(7L, edited.getRowVersionOrNull());
        assertEquals(2, edited.getVersions().size());
        assertSame(rejected, edited.getVersions().get(0));
        AgreementVersion after = edited.getVersions().get(1);
        assertEquals("V2", after.getId());
        assertEquals(2, after.getVersionNo());
        assertEquals(VersionStatus.DRAFT, after.getStatus());
        assertEquals("L1", after.getBasisLeadId());
        assertEquals("I1", after.getBasisIntakeId());
        assertEquals("S1", after.getCreatedByUserId());
        assertEquals(CREATED, after.getCreatedAt());
        assertEquals(NEW_PERIOD, after.getPeriod());
        assertEquals(CONSENT, after.getPhotoConsent());
        assertEquals(TERMS, after.getTerms());
        assertEquals(AdvertisingScope.SUNVIBE_CHANNELS, after.getAdvertisingScope());
        assertEquals(NOW, after.getUpdatedAt());
        assertEquals(Optional.of(after), edited.draft());
        // the agreement read is not changed by the edit
        assertSame(draft, agreement.getVersions().get(1));
        assertEquals(UPDATED, agreement.getUpdatedAt());
    }

    @Test
    void editDraft_noDraft_illegalState() {
        BrokerageAgreement awaiting = load(AgreementStatus.AWAITING_CONFIRMATION,
                List.of(version("V1", 1, VersionStatus.AWAITING_OWNER)));

        assertThrows(IllegalStateException.class, () -> awaiting.editDraft(NEW_PERIOD, CONSENT, TERMS, BrokerageFee.NONE,
                NOW));
    }

    @Test
    void load_nineArguments_nothingEntered() {
        AgreementVersion version = version("V1", 1, VersionStatus.DRAFT);

        assertEquals(PhotoConsent.NONE, version.getPhotoConsent());
        assertEquals(VersionTerms.NONE, version.getTerms());
        assertEquals(AdvertisingScope.SUNVIBE_CHANNELS, version.getAdvertisingScope());
    }

    @Test
    void load_elevenArguments_keepsTheContent() {
        AgreementVersion version = AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1",
                CREATED, UPDATED, CONSENT, TERMS);

        assertEquals(CONSENT, version.getPhotoConsent());
        assertEquals(TERMS, version.getTerms());
        assertThrows(NullPointerException.class, () -> AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD,
                "L1", "I1", "S1", CREATED, UPDATED, null, TERMS));
        assertThrows(NullPointerException.class, () -> AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD,
                "L1", "I1", "S1", CREATED, UPDATED, CONSENT, null));
    }

    @Test
    void create_newDraft_nothingEntered() {
        BrokerageAgreement agreement = BrokerageAgreement.create("A1", "U1", "P101", "P1",
                TransactionType.LONG_TERM_RENT, "O1", "L1", "I1", "V1", PERIOD, "S1", CREATED);

        AgreementVersion draft = agreement.draft().orElseThrow();
        assertEquals(PhotoConsent.NONE, draft.getPhotoConsent());
        assertEquals(VersionTerms.NONE, draft.getTerms());
        assertEquals(AdvertisingScope.SUNVIBE_CHANNELS, draft.getAdvertisingScope());
    }

    private static BrokerageAgreement load(AgreementStatus status, List<AgreementVersion> versions) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, UPDATED, 7L, versions);
    }

    private static AgreementVersion version(String id, int versionNo, VersionStatus status) {
        return AgreementVersion.load(id, versionNo, status, PERIOD, "L1", "I1", "S1", CREATED, UPDATED);
    }
}
