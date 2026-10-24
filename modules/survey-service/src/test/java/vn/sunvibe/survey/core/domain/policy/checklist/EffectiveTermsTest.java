package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.charge;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsCommon;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsSpace;

/** The G8 values each space gets in the preview (survey-checklist spec 4.7; W16-11). */
class EffectiveTermsTest {

    /** W16-11: one space overrides electricity; the other gets the common charge. */
    @Test
    void override_givesThatSpaceItsOwnChargeAndTheOthersTheCommonOne() {
        SourcedField<List<Fee>> liftFee = SourcedField.of(List.of(new Fee("Phí thang máy", Money.vnd(30_000),
                ChargeUnit.PER_MONTH)), ChecklistSource.OBSERVED_ON_SITE);
        TermsContent terms = new TermsContent(termsCommon(), List.of(
                termsSpace(SPACE_1, new ChargeOverrides(charge(4_000, ChargeUnit.PER_KWH), null, null, null, null),
                        liftFee),
                termsSpace(SPACE_2, ChargeOverrides.none(), null)));

        List<SpaceTerms> effective = EffectiveTerms.of(terms, List.of(observedSpace(SPACE_1, "P101"),
                observedSpace(SPACE_2, "P102")));

        assertEquals(new EffectiveCharge(charge(4_000, ChargeUnit.PER_KWH), ChargeOrigin.SPACE),
                effective.get(0).electricity());
        assertEquals(new EffectiveCharge(termsCommon().electricity(), ChargeOrigin.COMMON),
                effective.get(1).electricity());
        assertEquals(new EffectiveCharge(termsCommon().water(), ChargeOrigin.COMMON), effective.get(0).water());
        assertEquals(termsCommon().otherFees(), effective.get(0).commonFees());
        assertEquals(liftFee, effective.get(0).spaceFees());
        assertNull(effective.get(1).spaceFees());
        assertEquals(SPACE_2, effective.get(1).spaceId());
    }

    @Test
    void spaceWithoutEntry_getsTheCommonValues() {
        List<SpaceTerms> effective = EffectiveTerms.of(new TermsContent(termsCommon(), List.of()),
                List.of(observedSpace(SPACE_1, "A1")));

        assertEquals(new EffectiveCharge(termsCommon().serviceFee(), ChargeOrigin.COMMON),
                effective.get(0).serviceFee());
    }
}
