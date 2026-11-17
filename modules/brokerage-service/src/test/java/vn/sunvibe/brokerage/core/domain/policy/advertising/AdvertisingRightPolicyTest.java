package vn.sunvibe.brokerage.core.domain.policy.advertising;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup;
import vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingRight;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.domain.policy.advertising.AdvertisingRightPolicy.ConditionFacts;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup.BASIS;
import static vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup.ENDED;
import static vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup.NOT_IN_EFFECT;
import static vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup.NOT_STARTED;
import static vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup.OWNER;
import static vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup.PROPERTY;
import static vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup.SUSPENDED;
import static vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup.UNIT;

/**
 * Whether Sunvibe may advertise a unit now under one agreement, and which groups stop it (brokerage-agreement US-4
 * AC-1, AC-2; BR-6, BR-9, BR-12, BR-13; E05-S6 spec S6-D7, S6-D20; criteria AV-2, RT-2, RT-3 in the domain). Every
 * boundary with a fixed {@code now}: 2027-03-10 10:00 in Vietnam.
 */
class AdvertisingRightPolicyTest {

    private static final Instant NOW = Instant.parse("2027-03-10T03:00:00.123456Z");
    private static final Instant CREATED = Instant.parse("2027-02-01T03:00:00Z");
    private static final Instant SENT = Instant.parse("2027-02-02T03:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2027-02-03T03:00:00Z");
    private static final AgreementPeriod IN_EFFECT =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final AgreementPeriod AHEAD =
            new AgreementPeriod(LocalDate.parse("2027-04-01"), LocalDate.parse("2027-12-31"));
    private static final Instant AHEAD_STARTS_AT = Instant.parse("2027-03-31T17:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2027-12-31T17:00:00Z");
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, List.of());
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);
    private static final OwnerAnswer CONFIRMED = OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, CONFIRMED_AT);
    private static final ActivationDecision ACTIVATION = ActivationDecision.activated("M1",
            CONFIRMED_AT.plusSeconds(60), ActivationStatement.VERSION,
            new SignedDocument("HDMG-1", LocalDate.parse("2027-02-03"), "A", "B"));
    private static final ConditionFacts CLEAN = facts("USABLE", List.of(), "O1", true);

    @Test
    void answer_draftingOrAwaiting_notInEffectWithoutFacts() {
        List<BrokerageAgreement> agreements = List.of(
                agreement(AgreementStatus.DRAFTING, AgreementVersion.load("V1", 1, VersionStatus.DRAFT, IN_EFFECT, "L1",
                        "I1", "S1", CREATED, CREATED)),
                agreement(AgreementStatus.AWAITING_CONFIRMATION, AgreementVersion.load("V1", 1,
                        VersionStatus.OWNER_CONFIRMED, IN_EFFECT, "L1", "I1", "S1", CREATED, CONFIRMED_AT,
                        new PhotoConsent(true, true), TERMS, FEE, SENT, CONFIRMED)));

        for (BrokerageAgreement agreement : agreements) {
            assertFalse(AdvertisingRightPolicy.needsConditions(agreement, NOW));
            AdvertisingRight right = AdvertisingRightPolicy.answer(agreement, NOW, null);
            assertEquals(new AdvertisingRight(false, List.of(NOT_IN_EFFECT), null, null, false, false, null, null, null,
                    null, false), right);
        }
    }

    /** AV-2: before its start an activated version reads NOT_STARTED alone, with its start and terms as pending. */
    @Test
    void answer_activatedAhead_notStartedAloneWithPending() {
        BrokerageAgreement agreement = agreement(AgreementStatus.ACTIVE, version(VersionStatus.ACTIVATED, AHEAD,
                new PhotoConsent(true, true)));
        Instant justBefore = AHEAD_STARTS_AT.minus(1, ChronoUnit.MICROS);
        BrokerageAgreement effective = agreement.effectiveAt(justBefore);

        assertTrue(AdvertisingRightPolicy.needsConditions(effective, justBefore));
        AdvertisingRight right = AdvertisingRightPolicy.answer(effective, justBefore, CLEAN);

        assertEquals(new AdvertisingRight(false, List.of(NOT_STARTED), LocalDate.parse("2027-12-31"), ENDS_AT, false,
                false, null, LocalDate.parse("2027-04-01"), AHEAD_STARTS_AT, TERMS, false), right);
    }

    /** AV-2: at its start the version is in effect (applied by effectiveAt) and the unit may be advertised. */
    @Test
    void answer_atStartsAt_allowedWithTermsInEffect() {
        BrokerageAgreement agreement = agreement(AgreementStatus.ACTIVE, version(VersionStatus.ACTIVATED, AHEAD,
                new PhotoConsent(true, false)));
        BrokerageAgreement effective = agreement.effectiveAt(AHEAD_STARTS_AT);

        AdvertisingRight right = AdvertisingRightPolicy.answer(effective, AHEAD_STARTS_AT, CLEAN);

        assertEquals(new AdvertisingRight(true, List.of(), LocalDate.parse("2027-12-31"), ENDS_AT, true, false, TERMS,
                null, null, null, false), right);
    }

    @Test
    void answer_activeAtEndsAtMinusOneMicro_allowed() {
        BrokerageAgreement agreement = inEffect(AgreementStatus.ACTIVE, new PhotoConsent(true, true));
        Instant justBefore = ENDS_AT.minus(1, ChronoUnit.MICROS);

        AdvertisingRight right = AdvertisingRightPolicy.answer(agreement, justBefore, CLEAN);

        assertTrue(right.allowed());
        assertEquals(TERMS, right.termsInEffectOrNull());
        assertFalse(right.ended());
    }

    /** RT-2, S6-D20: from the end of the end date the answer is ENDED, while the stored status stays ACTIVE. */
    @Test
    void answer_activeAtEndsAt_endedTermsNullPhotosOff() {
        BrokerageAgreement agreement = inEffect(AgreementStatus.ACTIVE, new PhotoConsent(true, true));

        assertFalse(AdvertisingRightPolicy.needsConditions(agreement, ENDS_AT));
        AdvertisingRight right = AdvertisingRightPolicy.answer(agreement, ENDS_AT, null);

        assertEquals(new AdvertisingRight(false, List.of(ENDED), LocalDate.parse("2027-12-31"), ENDS_AT, false, false,
                null, null, null, null, true), right);
    }

    @Test
    void answer_storedFinalStates_ended() {
        for (AgreementStatus status : List.of(AgreementStatus.EXPIRED, AgreementStatus.TERMINATED,
                AgreementStatus.CANCELLED)) {
            BrokerageAgreement agreement = inEffect(status, new PhotoConsent(true, true));

            assertFalse(AdvertisingRightPolicy.needsConditions(agreement, NOW), status.name());
            AdvertisingRight right = AdvertisingRightPolicy.answer(agreement, NOW, null);
            assertEquals(List.of(ENDED), right.groups(), status.name());
            assertFalse(right.allowed());
            assertTrue(right.ended());
            assertNull(right.termsInEffectOrNull());
        }
    }

    @Test
    void answer_suspended_suspendedPlusConditionGroups() {
        BrokerageAgreement agreement = inEffect(AgreementStatus.SUSPENDED, new PhotoConsent(true, true));

        assertTrue(AdvertisingRightPolicy.needsConditions(agreement, NOW));
        assertEquals(List.of(SUSPENDED), AdvertisingRightPolicy.answer(agreement, NOW, CLEAN).groups());
        AdvertisingRight withStop = AdvertisingRightPolicy.answer(agreement, NOW,
                facts("USABLE", List.of("UNIT_STOP"), "O1", true));
        assertEquals(List.of(SUSPENDED, UNIT), withStop.groups());
        assertEquals(TERMS, withStop.termsInEffectOrNull());
        assertFalse(withStop.interiorPhotos());
    }

    /** brokerage-agreement BR-12, BR-2 to BR-4: E05-S1's groups on the version's basis, one to one, in their order. */
    @Test
    void answer_conditionGroups_mappedOneToOneInOrder() {
        BrokerageAgreement agreement = inEffect(AgreementStatus.ACTIVE, new PhotoConsent(true, false));

        assertEquals(List.of(BASIS), groups(agreement, facts("SUSPENDED", List.of(), "O1", true)));
        assertEquals(List.of(PROPERTY), groups(agreement, facts("USABLE", List.of("PROPERTY_STOP"), "O1", true)));
        assertEquals(List.of(UNIT), groups(agreement, facts("USABLE", List.of("UNIT_STOP"), "O1", true)));
        assertEquals(List.of(OWNER), groups(agreement, facts("USABLE", List.of(), "O1", false)));
        assertEquals(List.of(BASIS, PROPERTY, UNIT, OWNER), groups(agreement,
                facts("ENDED", List.of("UNIT_STOP", "PROPERTY_STOP"), "O1", false)));
        ConditionFacts coOwner = new ConditionFacts(new EligibilityPolicy.Basis("USABLE",
                List.of(new EligibilityPolicy.BasisUnit("U1", List.of()))), new EligibilityPolicy.Owner("O1",
                List.of(new EligibilityPolicy.Holding("O1", "OWNER", "UNVERIFIED"),
                        new EligibilityPolicy.Holding("C1", "CO_OWNER", "UNVERIFIED")), true), "O1");
        assertEquals(List.of(OWNER), groups(agreement, coOwner));

        AdvertisingRight availability = AdvertisingRightPolicy.answer(agreement, NOW,
                facts("USABLE", List.of("AVAILABILITY_DIFFERS"), "O1", true));
        assertTrue(availability.allowed());
        assertEquals(List.of(), availability.groups());
    }

    /** E05-S6b S6b-D1, BR-2: a basis intake that is not an entry of the unit in read 1 is no longer a basis. */
    @Test
    void answer_basisNotAnEntry_basis() {
        BrokerageAgreement agreement = inEffect(AgreementStatus.ACTIVE, new PhotoConsent(true, false));
        ConditionFacts noEntry = new ConditionFacts(null, new EligibilityPolicy.Owner("O1", List.of(), false), "O1");

        assertEquals(List.of(BASIS), groups(agreement, noEntry));
    }

    /** brokerage-agreement BR-1, BR-4: the agreement's owner is no longer the property's declared owner. */
    @Test
    void answer_declaredOwnerChanged_owner() {
        BrokerageAgreement agreement = inEffect(AgreementStatus.ACTIVE, new PhotoConsent(true, false));

        assertEquals(List.of(OWNER), groups(agreement, facts("USABLE", List.of(), "O2", true)));
    }

    /** RT-3, brokerage-agreement BR-9, BR-12: a photo flag is on only when allowed and the version gives it. */
    @Test
    void answer_photoConsent_onlyWhenAllowedAndGiven() {
        AdvertisingRight both = AdvertisingRightPolicy.answer(inEffect(AgreementStatus.ACTIVE,
                new PhotoConsent(true, true)), NOW, CLEAN);
        assertTrue(both.interiorPhotos());
        assertTrue(both.exteriorPhotos());

        AdvertisingRight interiorOnly = AdvertisingRightPolicy.answer(inEffect(AgreementStatus.ACTIVE,
                new PhotoConsent(true, false)), NOW, CLEAN);
        assertTrue(interiorOnly.interiorPhotos());
        assertFalse(interiorOnly.exteriorPhotos());

        AdvertisingRight none = AdvertisingRightPolicy.answer(inEffect(AgreementStatus.ACTIVE,
                new PhotoConsent(false, false)), NOW, CLEAN);
        assertTrue(none.allowed());
        assertFalse(none.interiorPhotos());
        assertFalse(none.exteriorPhotos());

        AdvertisingRight stopped = AdvertisingRightPolicy.answer(inEffect(AgreementStatus.ACTIVE,
                new PhotoConsent(true, true)), NOW, facts("USABLE", List.of("UNIT_STOP"), "O1", true));
        assertFalse(stopped.allowed());
        assertFalse(stopped.interiorPhotos());
        assertFalse(stopped.exteriorPhotos());
    }

    /** E05-S6 spec S6-D10, PLAN6-D1: ended in a final state, or from the end of the end date of the version that counts. */
    @Test
    void ended_finalStatesAndEndsAt() {
        for (AgreementStatus status : List.of(AgreementStatus.EXPIRED, AgreementStatus.TERMINATED,
                AgreementStatus.CANCELLED)) {
            assertTrue(AdvertisingRightPolicy.ended(inEffect(status, PhotoConsent.NONE), NOW), status.name());
        }
        BrokerageAgreement active = inEffect(AgreementStatus.ACTIVE, PhotoConsent.NONE);
        assertFalse(AdvertisingRightPolicy.ended(active, ENDS_AT.minus(1, ChronoUnit.MICROS)));
        assertTrue(AdvertisingRightPolicy.ended(active, ENDS_AT));
        assertTrue(AdvertisingRightPolicy.ended(inEffect(AgreementStatus.SUSPENDED, PhotoConsent.NONE),
                ENDS_AT.plus(Duration.ofDays(1))));
        assertFalse(AdvertisingRightPolicy.ended(agreement(AgreementStatus.DRAFTING, AgreementVersion.load("V1", 1,
                VersionStatus.DRAFT, IN_EFFECT, "L1", "I1", "S1", CREATED, CREATED)), ENDS_AT.plus(Duration.ofDays(1))));
    }

    private static List<AdvertisingReasonGroup> groups(BrokerageAgreement agreement, ConditionFacts facts) {
        return AdvertisingRightPolicy.answer(agreement, NOW, facts).groups();
    }

    private static ConditionFacts facts(String basisState, List<String> unitReasons, String declaredOwner,
                                        boolean accountEligible) {
        return new ConditionFacts(new EligibilityPolicy.Basis(basisState,
                List.of(new EligibilityPolicy.BasisUnit("U1", unitReasons))),
                new EligibilityPolicy.Owner(declaredOwner, List.of(new EligibilityPolicy.Holding(declaredOwner, "OWNER",
                        "UNVERIFIED")), accountEligible), declaredOwner);
    }

    private static BrokerageAgreement inEffect(AgreementStatus status, PhotoConsent consent) {
        return agreement(status, version(VersionStatus.EFFECTIVE, IN_EFFECT, consent));
    }

    private static AgreementVersion version(VersionStatus status, AgreementPeriod period, PhotoConsent consent) {
        return AgreementVersion.load("V1", 1, status, period, "L1", "I1", "S1", CREATED, CONFIRMED_AT, consent, TERMS,
                FEE, SENT, CONFIRMED, ACTIVATION);
    }

    private static BrokerageAgreement agreement(AgreementStatus status, AgreementVersion version) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, CREATED, 3L, List.of(version));
    }
}
