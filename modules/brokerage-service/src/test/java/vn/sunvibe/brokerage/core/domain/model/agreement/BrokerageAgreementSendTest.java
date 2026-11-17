package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sending a draft to the owner and the fee policy on the draft (brokerage-agreement US-1 AC-5, BR-5, BR-7; E05-S4 spec
 * S4-D5, S4-D6, 5.1): a complete draft becomes AWAITING_OWNER with its sending time, the agreement
 * AWAITING_CONFIRMATION, and nothing else changes; a DRAFT version never has a sending time.
 */
class BrokerageAgreementSendTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-11-21T03:15:00Z");
    private static final Instant NOW = Instant.parse("2026-11-22T08:30:00.654321Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1,
            List.of(new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH)));
    private static final BrokerageFee FEE = new BrokerageFee(
            FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 50), new FeeTier(12, 100)), null, null),
            7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3), 90);

    @Test
    void sendDraft_completeDraft_awaitingWithSentVersion() {
        BrokerageAgreement agreement = load(AgreementStatus.DRAFTING, List.of(complete("V1", 1)));

        BrokerageAgreement sent = agreement.sendDraft(NOW);

        assertEquals(AgreementStatus.AWAITING_CONFIRMATION, sent.getStatus());
        assertEquals(NOW, sent.getUpdatedAt());
        AgreementVersion version = sent.getVersions().getFirst();
        assertEquals(VersionStatus.AWAITING_OWNER, version.getStatus());
        assertEquals(NOW, version.getSentAtOrNull());
        assertEquals(NOW, version.getUpdatedAt());
        assertEquals(Optional.empty(), sent.draft());
        // the agreement read is not changed by the send
        assertEquals(AgreementStatus.DRAFTING, agreement.getStatus());
        assertNull(agreement.getVersions().getFirst().getSentAtOrNull());
    }

    @Test
    void sendDraft_noDraftOrMissingPart_illegalState() {
        BrokerageAgreement awaiting = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(AgreementVersion.load("V1", 1,
                VersionStatus.AWAITING_OWNER, PERIOD, "L1", "I1", "S1", CREATED, UPDATED, CONSENT, TERMS, FEE, UPDATED)));
        BrokerageAgreement incomplete = load(AgreementStatus.DRAFTING, List.of(AgreementVersion.load("V1", 1,
                VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED, UPDATED, CONSENT, TERMS, BrokerageFee.NONE,
                null)));
        BrokerageAgreement created = BrokerageAgreement.create("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT,
                "O1", "L1", "I1", "V1", PERIOD, "S1", CREATED);

        assertThrows(IllegalStateException.class, () -> awaiting.sendDraft(NOW));
        assertThrows(IllegalStateException.class, () -> incomplete.sendDraft(NOW));
        assertThrows(IllegalStateException.class, () -> created.sendDraft(NOW));
    }

    @Test
    void sendDraft_keepsEverythingElse() {
        AgreementVersion rejected = AgreementVersion.load("V1", 1, VersionStatus.OWNER_REJECTED, PERIOD, "L1", "I1", "S0",
                CREATED, UPDATED);
        AgreementVersion draft = complete("V2", 2);
        BrokerageAgreement agreement = load(AgreementStatus.DRAFTING, List.of(rejected, draft));

        BrokerageAgreement sent = agreement.sendDraft(NOW);

        assertEquals("A1", sent.getId());
        assertEquals("U1", sent.getUnitId());
        assertEquals("P101", sent.getUnitCodeOrNull());
        assertEquals("P1", sent.getPropertyId());
        assertEquals(TransactionType.LONG_TERM_RENT, sent.getTransactionType());
        assertEquals("O1", sent.getOwnerUserId());
        assertEquals("L1", sent.getBasisLeadId());
        assertEquals("S1", sent.getCreatedByUserId());
        assertEquals(CREATED, sent.getCreatedAt());
        assertEquals(7L, sent.getRowVersionOrNull());
        assertSame(rejected, sent.getVersions().get(0));
        AgreementVersion after = sent.getVersions().get(1);
        assertEquals("V2", after.getId());
        assertEquals(2, after.getVersionNo());
        assertEquals(PERIOD, after.getPeriod());
        assertEquals("L1", after.getBasisLeadId());
        assertEquals("I1", after.getBasisIntakeId());
        assertEquals("S1", after.getCreatedByUserId());
        assertEquals(CREATED, after.getCreatedAt());
        assertEquals(CONSENT, after.getPhotoConsent());
        assertEquals(TERMS, after.getTerms());
        assertEquals(FEE, after.getFee());
        assertEquals(AdvertisingScope.SUNVIBE_CHANNELS, after.getAdvertisingScope());
    }

    @Test
    void editDraft_feePolicy_keptOnTheDraftVersion() {
        BrokerageAgreement agreement = load(AgreementStatus.DRAFTING,
                List.of(AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED, UPDATED)));

        BrokerageAgreement edited = agreement.editDraft(PERIOD, CONSENT, TERMS, FEE, NOW);
        BrokerageAgreement cleared = edited.editDraft(PERIOD, CONSENT, TERMS, BrokerageFee.NONE, NOW);

        assertEquals(FEE, edited.draft().orElseThrow().getFee());
        assertNull(edited.draft().orElseThrow().getSentAtOrNull());
        assertEquals(BrokerageFee.NONE, cleared.draft().orElseThrow().getFee());
        assertEquals(BrokerageFee.NONE, agreement.draft().orElseThrow().getFee());
    }

    /** brokerage-agreement BR-5: a draft is a version not sent yet, so a stored DRAFT with a sending time is a fault. */
    @Test
    void version_draftWithSentAt_refused() {
        assertThrows(IllegalArgumentException.class, () -> AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD,
                "L1", "I1", "S1", CREATED, UPDATED, CONSENT, TERMS, FEE, UPDATED));
        assertThrows(NullPointerException.class, () -> AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD,
                "L1", "I1", "S1", CREATED, UPDATED, CONSENT, TERMS, null, null));
        assertEquals(BrokerageFee.NONE, AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1",
                CREATED, UPDATED, CONSENT, TERMS).getFee());
        assertNull(AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED, UPDATED)
                .getSentAtOrNull());
    }

    private static AgreementVersion complete(String id, int versionNo) {
        return AgreementVersion.load(id, versionNo, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED, UPDATED,
                CONSENT, TERMS, FEE, null);
    }

    private static BrokerageAgreement load(AgreementStatus status, List<AgreementVersion> versions) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, UPDATED, 7L, versions);
    }
}
