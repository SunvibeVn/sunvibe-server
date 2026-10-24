package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The G8 values each space gets (survey-checklist spec 4.7; BR-5 "chênh lệch gắn phần"). */
public final class EffectiveTerms {

    private EffectiveTerms() {
    }

    public static List<SpaceTerms> of(TermsContent terms, List<ObservedSpace> spaces) {
        Map<String, TermsSpace> bySpace = terms.spaces().stream()
                .collect(Collectors.toMap(TermsSpace::spaceId, Function.identity(), (left, right) -> left));
        TermsCommon common = terms.common();
        return spaces.stream().map(space -> {
            TermsSpace entry = bySpace.get(space.spaceId());
            ChargeOverrides overrides = entry == null || entry.chargeOverrides() == null ? ChargeOverrides.none()
                    : entry.chargeOverrides();
            return new SpaceTerms(space.spaceId(), effective(overrides.electricity(), common.electricity()),
                    effective(overrides.water(), common.water()), effective(overrides.internet(), common.internet()),
                    effective(overrides.parking(), common.parking()),
                    effective(overrides.serviceFee(), common.serviceFee()), common.otherFees(),
                    entry == null ? null : entry.spaceFees());
        }).toList();
    }

    private static EffectiveCharge effective(SourcedField<Charge> override, SourcedField<Charge> common) {
        return override != null && override.isValue() ? new EffectiveCharge(override, ChargeOrigin.SPACE)
                : new EffectiveCharge(common, ChargeOrigin.COMMON);
    }
}
