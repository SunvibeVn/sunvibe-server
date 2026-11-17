package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 7-day answer window and its lapse (brokerage-agreement US-2 AC-3; BR-5, BR-10; E05-S5 spec S5-D8, S5-D9, S5-D19,
 * OC-6): a version sent at T may be answered until T + 7 days, exclusive; from then on it lapses, and an agreement that
 * waited only for it is drafting again. The exact boundary is proved here with a fixed {@code now}.
 */
class BrokerageAgreementLapseTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final Instant SENT = Instant.parse("2026-11-22T08:30:00.654321Z");
    private static final Instant DEADLINE = SENT.plus(Duration.ofDays(7));
    private static final Instant JUST_BEFORE = DEADLINE.minus(1, ChronoUnit.MICROS);
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, List.of());
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);

    @Test
    void answerBy_sentAtPlusSevenDays() {
        assertEquals(Duration.ofDays(7), AgreementVersion.ANSWER_WINDOW);
        assertEquals(Instant.parse("2026-11-29T08:30:00.654321Z"), awaiting("V1", 1, SENT).answerByOrNull());
        assertNull(AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED, CREATED)
                .answerByOrNull());
    }

    @Test
    void answerableAt_justBeforeAndAtTheDeadline() {
        AgreementVersion version = awaiting("V1", 1, SENT);

        assertTrue(version.answerableAt(SENT));
        assertTrue(version.answerableAt(JUST_BEFORE));
        assertFalse(version.answerableAt(DEADLINE));
        assertFalse(version.answerableAt(DEADLINE.plusSeconds(1)));
    }

    @Test
    void withDueLapse_atTheDeadline_lapsedAndDrafting() {
        BrokerageAgreement agreement = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(awaiting("V1", 1, SENT)));

        assertSame(agreement, agreement.withDueLapse(JUST_BEFORE));
        assertNull(agreement.dueLapseOrNull(JUST_BEFORE));

        BrokerageAgreement lapsed = agreement.withDueLapse(DEADLINE);

        assertEquals(AgreementStatus.DRAFTING, lapsed.getStatus());
        assertEquals(DEADLINE, lapsed.getUpdatedAt());
        assertEquals(7L, lapsed.getRowVersionOrNull());
        AgreementVersion version = lapsed.getVersions().getFirst();
        assertEquals(VersionStatus.LAPSED, version.getStatus());
        assertEquals(DEADLINE, version.getUpdatedAt());
        assertEquals(SENT, version.getSentAtOrNull());
        assertNull(version.getOwnerAnswerOrNull());
        assertEquals(TERMS, version.getTerms());
        assertEquals("V1", agreement.dueLapseOrNull(DEADLINE).getId());
        assertEquals(DEADLINE, agreement.dueLapseOrNull(DEADLINE).answerByOrNull());
        // the agreement read is not changed
        assertEquals(AgreementStatus.AWAITING_CONFIRMATION, agreement.getStatus());
        assertEquals(VersionStatus.AWAITING_OWNER, agreement.getVersions().getFirst().getStatus());
    }

    @Test
    void withDueLapse_answeredOrDraft_unchanged() {
        Instant late = DEADLINE.plus(Duration.ofDays(30));
        AgreementVersion confirmed = answered("V1", 1, VersionStatus.OWNER_CONFIRMED,
                OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, SENT.plusSeconds(60)));
        AgreementVersion rejected = answered("V1", 1, VersionStatus.OWNER_REJECTED,
                OwnerAnswer.rejected("O1", null, SENT.plusSeconds(60)));
        List<BrokerageAgreement> agreements = List.of(
                load(AgreementStatus.DRAFTING, List.of(rejected, draft("V2", 2))),
                load(AgreementStatus.DRAFTING, List.of(draft("V1", 1))));

        for (BrokerageAgreement agreement : agreements) {
            assertSame(agreement, agreement.withDueLapse(late));
            assertNull(agreement.dueLapseOrNull(late));
        }
        // E05-S6 spec S6-D5: a confirmed version lapses 14 days after the confirmation, so it is unchanged just before.
        BrokerageAgreement awaitingActivation = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(confirmed));
        Instant justBeforeActivateBy = confirmed.activateByOrNull().minus(1, ChronoUnit.MICROS);
        assertSame(awaitingActivation, awaitingActivation.withDueLapse(justBeforeActivateBy));
        assertNull(awaitingActivation.dueLapseOrNull(justBeforeActivateBy));
    }

    /** E05-S5 spec S5-D8 (revision 3): a version without a sending time has no deadline, so it never lapses. */
    @Test
    void withDueLapse_noSentAt_neverDue() {
        AgreementVersion unsent = AgreementVersion.load("V1", 1, VersionStatus.AWAITING_OWNER, PERIOD, "L1", "I1", "S1",
                CREATED, CREATED);
        BrokerageAgreement agreement = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(unsent));
        Instant muchLater = SENT.plus(Duration.ofDays(400));

        assertNull(unsent.answerByOrNull());
        assertFalse(unsent.answerableAt(CREATED));
        assertFalse(unsent.answerableAt(muchLater));
        assertSame(agreement, agreement.withDueLapse(muchLater));
        assertNull(agreement.dueLapseOrNull(muchLater));
    }

    /** E05-S5 spec S5-D9: an agreement in effect keeps its status when a later version lapses. */
    @Test
    void withDueLapse_activeAgreement_keepsItsStatus() {
        AgreementVersion effective = AgreementVersion.load("V1", 1, VersionStatus.EFFECTIVE, PERIOD, "L1", "I1", "S1",
                CREATED, CREATED, CONSENT, TERMS, FEE, CREATED,
                OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, CREATED));
        BrokerageAgreement agreement = load(AgreementStatus.ACTIVE, List.of(effective, awaiting("V2", 2, SENT)));

        BrokerageAgreement lapsed = agreement.withDueLapse(DEADLINE);

        assertEquals(AgreementStatus.ACTIVE, lapsed.getStatus());
        assertEquals(VersionStatus.LAPSED, lapsed.getVersions().get(1).getStatus());
        assertSame(effective, lapsed.getVersions().getFirst());
    }

    private static AgreementVersion awaiting(String id, int versionNo, Instant sentAt) {
        return AgreementVersion.load(id, versionNo, VersionStatus.AWAITING_OWNER, PERIOD, "L1", "I1", "S1", CREATED,
                sentAt, CONSENT, TERMS, FEE, sentAt);
    }

    private static AgreementVersion answered(String id, int versionNo, VersionStatus status, OwnerAnswer answer) {
        return AgreementVersion.load(id, versionNo, status, PERIOD, "L1", "I1", "S1", CREATED, answer.answeredAt(),
                CONSENT, TERMS, FEE, SENT, answer);
    }

    private static AgreementVersion draft(String id, int versionNo) {
        return AgreementVersion.load(id, versionNo, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED, CREATED,
                CONSENT, TERMS, FEE, null);
    }

    private static BrokerageAgreement load(AgreementStatus status, List<AgreementVersion> versions) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, CREATED, 7L, versions);
    }
}
