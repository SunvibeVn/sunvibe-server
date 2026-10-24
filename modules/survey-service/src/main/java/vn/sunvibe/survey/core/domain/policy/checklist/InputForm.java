package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;

/**
 * The stored content of a group as GET shows it and a save compares it, without metadata or server fields
 * (survey-checklist spec PD-4, PD-5): canonical against the G1 content, and in PARTS a stored null shared wet area as
 * UNKNOWN and a stored null {@code chargeOverrides} as five nulls.
 */
public final class InputForm {

    private InputForm() {
    }

    public static GroupContent ofStored(GroupDocument stored, IdentificationContent g1) {
        GroupContent canonical = TemplateV1Rules.canonical(stored.content(), g1);
        if (g1.rentalScope() != RentalScope.PARTS) {
            return canonical;
        }
        return switch (canonical) {
            case ConditionContent g -> new ConditionContent(
                    g.sharedKitchen() == null ? Field.unknown() : g.sharedKitchen(),
                    g.sharedBathroom() == null ? Field.unknown() : g.sharedBathroom(), g.spaces());
            case TermsContent g -> new TermsContent(g.common(), g.spaces().stream()
                    .map(space -> space.chargeOverrides() != null ? space : new TermsSpace(space.spaceId(),
                            space.monthlyRent(), space.deposit(), space.paymentPeriodMonths(),
                            space.minimumTermMonths(), ChargeOverrides.none(), space.spaceFees()))
                    .toList());
            default -> canonical;
        };
    }
}
