package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The owner's confirmation and rejection of the version waiting for them (brokerage-agreement US-2 AC-2, AC-3; BR-5,
 * BR-10; E05-S5 spec S5-D6, S5-D7, S5-D9, S5-D11): each records the answer on that version only; a confirmation keeps
 * the agreement awaiting confirmation, a rejection makes it drafting again; nothing else changes.
 */
class BrokerageAgreementOwnerAnswerTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final Instant SENT = Instant.parse("2026-11-22T08:30:00.654321Z");
    private static final Instant NOW = SENT.plus(Duration.ofHours(30));
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, true);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1,
            List.of(new MandatoryFee("Water", FeeBasis.PROVIDER_TARIFF, null, null)));
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT,
            List.of(new FeeTier(1, 50), new FeeTier(12, 100)), null, null), 7,
            FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3), 90);

    @Test
    void confirmByOwner_answerable_confirmedAgreementStillAwaiting() {
        BrokerageAgreement agreement = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(awaiting("V1", 1)));

        BrokerageAgreement confirmed = agreement.confirmByOwner(1, "O1", AgreementStatement.VERSION, NOW);

        assertEquals(AgreementStatus.AWAITING_CONFIRMATION, confirmed.getStatus());
        assertEquals(NOW, confirmed.getUpdatedAt());
        assertEquals(7L, confirmed.getRowVersionOrNull());
        AgreementVersion version = confirmed.getVersions().getFirst();
        assertEquals(VersionStatus.OWNER_CONFIRMED, version.getStatus());
        assertEquals(OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, NOW), version.getOwnerAnswerOrNull());
        assertEquals(NOW, version.getUpdatedAt());
        assertEquals(SENT, version.getSentAtOrNull());
        // the agreement read is not changed
        assertEquals(VersionStatus.AWAITING_OWNER, agreement.getVersions().getFirst().getStatus());
    }

    @Test
    void rejectByOwner_answerable_rejectedAndDraftingAgain() {
        BrokerageAgreement agreement = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(awaiting("V1", 1)));

        BrokerageAgreement rejected = agreement.rejectByOwner(1, "O1", "  The rent is too low.  ", NOW);

        assertEquals(AgreementStatus.DRAFTING, rejected.getStatus());
        assertEquals(NOW, rejected.getUpdatedAt());
        AgreementVersion version = rejected.getVersions().getFirst();
        assertEquals(VersionStatus.OWNER_REJECTED, version.getStatus());
        assertEquals(OwnerAnswer.rejected("O1", "The rent is too low.", NOW), version.getOwnerAnswerOrNull());
        assertEquals(NOW, version.getUpdatedAt());
        assertEquals(java.util.Optional.empty(), rejected.draft());
    }

    @Test
    void answer_notAnswerable_illegalState() {
        Instant deadline = SENT.plus(AgreementVersion.ANSWER_WINDOW);
        BrokerageAgreement awaiting = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(awaiting("V1", 1)));
        BrokerageAgreement answered = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(AgreementVersion.load("V1", 1,
                VersionStatus.OWNER_CONFIRMED, PERIOD, "L1", "I1", "S1", CREATED, NOW, CONSENT, TERMS, FEE, SENT,
                OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, NOW))));
        BrokerageAgreement draft = load(AgreementStatus.DRAFTING, List.of(AgreementVersion.load("V1", 1,
                VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED, CREATED, CONSENT, TERMS, FEE, null)));

        List<Executable> refused = List.of(
                () -> awaiting.confirmByOwner(1, "O1", AgreementStatement.VERSION, deadline),
                () -> awaiting.rejectByOwner(1, "O1", null, deadline),
                () -> answered.confirmByOwner(1, "O1", AgreementStatement.VERSION, NOW),
                () -> answered.rejectByOwner(1, "O1", null, NOW),
                () -> draft.confirmByOwner(1, "O1", AgreementStatement.VERSION, NOW),
                () -> draft.rejectByOwner(1, "O1", null, NOW),
                () -> awaiting.confirmByOwner(2, "O1", AgreementStatement.VERSION, NOW),
                () -> awaiting.rejectByOwner(2, "O1", null, NOW));

        for (int index = 0; index < refused.size(); index++) {
            assertThrows(IllegalStateException.class, refused.get(index), "case " + index);
        }
    }

    @Test
    void answer_keepsContentAndOtherVersions() {
        AgreementVersion first = AgreementVersion.load("V1", 1, VersionStatus.OWNER_REJECTED, PERIOD, "L1", "I1", "S0",
                CREATED, SENT, CONSENT, TERMS, FEE, CREATED, OwnerAnswer.rejected("O1", "No", SENT));
        BrokerageAgreement agreement = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(first, awaiting("V2", 2)));

        BrokerageAgreement confirmed = agreement.confirmByOwner(2, "O1", AgreementStatement.VERSION, NOW);

        assertSame(first, confirmed.getVersions().getFirst());
        AgreementVersion after = confirmed.getVersions().get(1);
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
        assertEquals(SENT, after.getSentAtOrNull());
        assertEquals("A1", confirmed.getId());
        assertEquals("U1", confirmed.getUnitId());
        assertEquals("P101", confirmed.getUnitCodeOrNull());
        assertEquals("O1", confirmed.getOwnerUserId());
        assertEquals("L1", confirmed.getBasisLeadId());
        assertEquals(CREATED, confirmed.getCreatedAt());

        BrokerageAgreement rejected = agreement.rejectByOwner(2, "O1", null, NOW);
        assertSame(first, rejected.getVersions().getFirst());
        assertEquals(TERMS, rejected.getVersions().get(1).getTerms());
        assertEquals(FEE, rejected.getVersions().get(1).getFee());
    }

    /** E05-S5 spec S5-D11: an answer needs a sending time, and a draft or a version still waiting holds none. */
    @Test
    void version_answerOnDraftOrAwaiting_refused() {
        OwnerAnswer answer = OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, NOW);

        assertThrows(IllegalArgumentException.class, () -> AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD,
                "L1", "I1", "S1", CREATED, NOW, CONSENT, TERMS, FEE, null, answer));
        assertThrows(IllegalArgumentException.class, () -> AgreementVersion.load("V1", 1, VersionStatus.AWAITING_OWNER,
                PERIOD, "L1", "I1", "S1", CREATED, NOW, CONSENT, TERMS, FEE, SENT, answer));
        assertThrows(IllegalArgumentException.class, () -> AgreementVersion.load("V1", 1, VersionStatus.OWNER_CONFIRMED,
                PERIOD, "L1", "I1", "S1", CREATED, NOW, CONSENT, TERMS, FEE, null, answer));
        assertDoesNotThrow(() -> AgreementVersion.load("V1", 1, VersionStatus.OWNER_CONFIRMED, PERIOD, "L1", "I1", "S1",
                CREATED, NOW, CONSENT, TERMS, FEE, SENT, answer));
        // an answered state without a stored answer stays loadable, as the fixture rows of earlier tests hold it
        assertDoesNotThrow(() -> AgreementVersion.load("V1", 1, VersionStatus.OWNER_REJECTED, PERIOD, "L1", "I1", "S1",
                CREATED, NOW, CONSENT, TERMS, FEE, null, null));
    }

    private static AgreementVersion awaiting(String id, int versionNo) {
        return AgreementVersion.load(id, versionNo, VersionStatus.AWAITING_OWNER, PERIOD, "L1", "I1", "S1", CREATED,
                SENT, CONSENT, TERMS, FEE, SENT);
    }

    private static BrokerageAgreement load(AgreementStatus status, List<AgreementVersion> versions) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, CREATED, 7L, versions);
    }
}
