package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A new draft version after the owner's rejection or a lapse (brokerage-agreement US-2 AC-3, E-6; BR-5; E05-S5 spec
 * S5-D10, ND-1 rule part): version n + 1, a DRAFT copy of the latest version's content, opened only on a drafting
 * agreement with no pending version; earlier versions never change.
 */
class BrokerageAgreementNewDraftTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final Instant SENT = Instant.parse("2026-11-22T08:30:00.654321Z");
    private static final Instant NOW = Instant.parse("2026-11-25T01:00:00.000001Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, true);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.AMOUNT, null, 16_000_000L), 12, 1,
            List.of(new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                    new MandatoryFee("Water", FeeBasis.PROVIDER_TARIFF, null, null)));
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT,
            List.of(new FeeTier(1, 50), new FeeTier(12, 100)), null, null), 7,
            FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 20), new FeeTier(24, 30)), null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3), 90);

    @Test
    void openDraft_afterRejection_copyOfLatestContent() {
        BrokerageAgreement agreement = load(AgreementStatus.DRAFTING, List.of(rejected("V1", 1)));

        BrokerageAgreement opened = agreement.openDraft("V2", "S2", NOW);

        assertEquals(AgreementStatus.DRAFTING, opened.getStatus());
        assertEquals(NOW, opened.getUpdatedAt());
        assertEquals(7L, opened.getRowVersionOrNull());
        assertEquals(2, opened.getVersions().size());
        AgreementVersion draft = opened.getVersions().get(1);
        assertEquals("V2", draft.getId());
        assertEquals(2, draft.getVersionNo());
        assertEquals(VersionStatus.DRAFT, draft.getStatus());
        assertEquals(PERIOD, draft.getPeriod());
        assertEquals(CONSENT, draft.getPhotoConsent());
        assertEquals(TERMS, draft.getTerms());
        assertEquals(FEE, draft.getFee());
        assertEquals(FEE.formulaOrNull().tiers(), draft.getFee().formulaOrNull().tiers());
        assertEquals(FEE.renewalOrNull().tiers(), draft.getFee().renewalOrNull().tiers());
        assertEquals("L1", draft.getBasisLeadId());
        assertEquals("I1", draft.getBasisIntakeId());
        assertEquals("S2", draft.getCreatedByUserId());
        assertEquals(NOW, draft.getCreatedAt());
        assertEquals(NOW, draft.getUpdatedAt());
        assertNull(draft.getSentAtOrNull());
        assertNull(draft.getOwnerAnswerOrNull());
        assertSame(draft, opened.draft().orElseThrow());
    }

    @Test
    void openDraft_pendingOrNotDrafting_illegalState() {
        List<BrokerageAgreement> refused = List.of(
                load(AgreementStatus.AWAITING_CONFIRMATION, List.of(version("V1", 1, VersionStatus.AWAITING_OWNER,
                        SENT, null))),
                load(AgreementStatus.AWAITING_CONFIRMATION, List.of(version("V1", 1, VersionStatus.OWNER_CONFIRMED,
                        SENT, OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, SENT)))),
                load(AgreementStatus.DRAFTING, List.of(version("V1", 1, VersionStatus.DRAFT, null, null))),
                load(AgreementStatus.CANCELLED, List.of(version("V1", 1, VersionStatus.CANCELLED, SENT, null))),
                load(AgreementStatus.AWAITING_CONFIRMATION, List.of(rejected("V1", 1))));

        for (BrokerageAgreement agreement : refused) {
            assertThrows(IllegalStateException.class, () -> agreement.openDraft("V9", "S2", NOW),
                    agreement.getStatus() + " " + agreement.getVersions().getFirst().getStatus());
        }
    }

    @Test
    void openDraft_keepsEarlierVersions() {
        AgreementVersion first = rejected("V1", 1);
        AgreementVersion second = version("V2", 2, VersionStatus.LAPSED, SENT, null);
        BrokerageAgreement agreement = load(AgreementStatus.DRAFTING, List.of(first, second));

        BrokerageAgreement opened = agreement.openDraft("V3", "S2", NOW);

        assertEquals(3, opened.getVersions().size());
        assertSame(first, opened.getVersions().get(0));
        assertSame(second, opened.getVersions().get(1));
        assertEquals(3, opened.getVersions().get(2).getVersionNo());
        assertEquals(VersionStatus.DRAFT, opened.getVersions().get(2).getStatus());
        // the agreement read is not changed
        assertEquals(2, agreement.getVersions().size());
    }

    private static AgreementVersion rejected(String id, int versionNo) {
        return version(id, versionNo, VersionStatus.OWNER_REJECTED, SENT,
                OwnerAnswer.rejected("O1", "The rent is too low.", SENT.plusSeconds(3600)));
    }

    private static AgreementVersion version(String id, int versionNo, VersionStatus status, Instant sentAtOrNull,
                                            OwnerAnswer answerOrNull) {
        return AgreementVersion.load(id, versionNo, status, PERIOD, "L1", "I1", "S1", CREATED, CREATED, CONSENT, TERMS,
                FEE, sentAtOrNull, answerOrNull);
    }

    private static BrokerageAgreement load(AgreementStatus status, List<AgreementVersion> versions) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, CREATED, 7L, versions);
    }
}
