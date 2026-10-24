package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.AccessExtent;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheckResult;
import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.Fit;
import vn.sunvibe.survey.core.domain.model.checklist.FitAssessment;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourceKind;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The preview lists, computed from the stored groups on every read (survey-checklist spec 4.7, US-2 AC-1, AC-2, AC-4,
 * BR-11, BR-17, BR-18, E-1). Every group is read as GET shows it ({@link InputForm#ofStored}), so a space without
 * entries lists each needed field. Missing items are in group order, then space order; a field of the direct-check
 * catalogue (spec A-28) counts only when checked on site. Nothing here blocks a save.
 */
public final class ReadinessCalculator {

    private ReadinessCalculator() {
    }

    public static Readiness of(Map<ChecklistGroup, GroupDocument> groups, LocationDifference difference,
                               List<EarlierHazard> earlier) {
        IdentificationContent g1 = (IdentificationContent) groups.get(ChecklistGroup.IDENTIFICATION).content();
        PartsContent parts = (PartsContent) read(groups, ChecklistGroup.PARTS, g1);
        DimensionsContent dimensions = (DimensionsContent) read(groups, ChecklistGroup.DIMENSIONS, g1);
        ConditionContent condition = (ConditionContent) read(groups, ChecklistGroup.CONDITION, g1);
        SafetyContent safety = (SafetyContent) read(groups, ChecklistGroup.SAFETY, g1);
        TermsContent terms = (TermsContent) read(groups, ChecklistGroup.TERMS, g1);

        Missing missing = new Missing();
        identification(missing, g1, difference);
        missing.field(ChecklistGroup.PARTS, null, "accessRoute", parts.accessRoute());
        missing.field(ChecklistGroup.PARTS, null, "sharedAreas", parts.sharedAreas());
        for (PartsSpace space : parts.spaces()) {
            missing.field(ChecklistGroup.PARTS, space.spaceId(), "floor", space.floor());
            missing.field(ChecklistGroup.PARTS, space.spaceId(), "privateAreas", space.privateAreas());
            missing.field(ChecklistGroup.PARTS, space.spaceId(), "actualAccess", space.actualAccess());
        }
        for (DimensionsSpace space : dimensions.spaces()) {
            missing.sourced(ChecklistGroup.DIMENSIONS, space.spaceId(), "area", space.area());
            missing.sourced(ChecklistGroup.DIMENSIONS, space.spaceId(), "bedrooms", space.bedrooms());
            missing.sourced(ChecklistGroup.DIMENSIONS, space.spaceId(), "privateBathrooms", space.privateBathrooms());
            missing.sourced(ChecklistGroup.DIMENSIONS, space.spaceId(), "sharedBathroomUse",
                    space.sharedBathroomUse());
        }
        if (g1.rentalScope() == RentalScope.PARTS) {
            missing.areaCheck(null, "sharedKitchen", condition.sharedKitchen());
            missing.areaCheck(null, "sharedBathroom", condition.sharedBathroom());
        }
        for (ConditionSpace space : condition.spaces()) {
            missing.field(ChecklistGroup.CONDITION, space.spaceId(), "occupancy", space.occupancy());
            missing.areaCheck(space.spaceId(), "mainSpace", space.mainSpace());
            missing.areaCheck(space.spaceId(), "kitchen", space.kitchen());
            missing.areaCheck(space.spaceId(), "bathroom", space.bathroom());
        }
        missing.field(ChecklistGroup.SAFETY, null, "egress", safety.egress());
        missing.field(ChecklistGroup.SAFETY, null, "accessRestrictions", safety.accessRestrictions());
        missing.field(ChecklistGroup.SAFETY, null, "hazards", safety.hazards());
        missing.add(ChecklistGroup.EVIDENCE, null, null, MissingProblem.EVIDENCE_PENDING);

        return new Readiness(false, Readiness.EVIDENCE_NOT_AVAILABLE, List.copyOf(missing.items),
                unconfirmed(condition, terms), warnings(parts, safety, earlier));
    }

    private static void identification(Missing missing, IdentificationContent g1, LocationDifference difference) {
        ChecklistGroup group = ChecklistGroup.IDENTIFICATION;
        missing.sourced(group, null, "observedAddress", g1.observedAddress());
        if (difference.fromLead() && g1.differenceNote() == null) {
            missing.add(group, null, "differenceNote", MissingProblem.DIFFERENCE_NOTE_MISSING);
        }
        // US-4 AC-3: an unknown type stays listed, also out of scope
        missing.field(group, null, "propertyType", g1.propertyType());
        Field<FitAssessment> fit = g1.fit();
        if (fit.isValue() && fit.value().assessment() != Fit.IN_SCOPE) {
            missing.add(group, null, "fit", MissingProblem.NOT_IN_SCOPE);
        } else {
            missing.field(group, null, "fit", fit);
        }
        if (g1.rentalScope() == null) {
            missing.add(group, null, "rentalScope", MissingProblem.SCOPE_MISSING);
        }
        if (g1.spaces().isEmpty()) {
            missing.add(group, null, "spaces", MissingProblem.NO_SPACES);
        }
    }

    /**
     * survey-checklist US-2 AC-2, BR-5: per-space terms and availability still unknown, the common lists and texts,
     * and a common charge still unknown once, unless every space overrides it with a value.
     */
    private static List<UnconfirmedTerm> unconfirmed(ConditionContent condition, TermsContent terms) {
        List<UnconfirmedTerm> items = new ArrayList<>();
        for (ConditionSpace space : condition.spaces()) {
            if (space.availableFrom().isUnknown()) {
                items.add(new UnconfirmedTerm(ChecklistGroup.CONDITION, space.spaceId(), "availableFrom"));
            }
        }
        TermsCommon common = terms.common();
        Map<String, SourcedField<Charge>> charges = new LinkedHashMap<>();
        charges.put("electricity", common.electricity());
        charges.put("water", common.water());
        charges.put("internet", common.internet());
        charges.put("parking", common.parking());
        charges.put("serviceFee", common.serviceFee());
        Map<String, Function<ChargeOverrides, SourcedField<Charge>>> members = Map.of(
                "electricity", ChargeOverrides::electricity, "water", ChargeOverrides::water,
                "internet", ChargeOverrides::internet, "parking", ChargeOverrides::parking,
                "serviceFee", ChargeOverrides::serviceFee);
        charges.forEach((name, charge) -> {
            boolean everySpaceOverrides = !terms.spaces().isEmpty() && terms.spaces().stream().allMatch(space ->
                    space.chargeOverrides() != null && members.get(name).apply(space.chargeOverrides()) != null
                            && members.get(name).apply(space.chargeOverrides()).isValue());
            if (charge.isUnknown() && !everySpaceOverrides) {
                items.add(new UnconfirmedTerm(ChecklistGroup.TERMS, null, name));
            }
        });
        unknown(items, null, "otherFees", common.otherFees());
        unknown(items, null, "houseRules", common.houseRules());
        unknown(items, null, "pets", common.pets());
        unknown(items, null, "handoverMaintenance", common.handoverMaintenance());
        for (TermsSpace space : terms.spaces()) {
            unknown(items, space.spaceId(), "monthlyRent", space.monthlyRent());
            unknown(items, space.spaceId(), "deposit", space.deposit());
            unknown(items, space.spaceId(), "paymentPeriodMonths", space.paymentPeriodMonths());
            unknown(items, space.spaceId(), "minimumTermMonths", space.minimumTermMonths());
        }
        return List.copyOf(items);
    }

    private static void unknown(List<UnconfirmedTerm> items, String spaceId, String field, SourcedField<?> term) {
        if (term.isUnknown()) {
            items.add(new UnconfirmedTerm(ChecklistGroup.TERMS, spaceId, field));
        }
    }

    /** spec 4.7: open important hazards, earlier hazards nothing addresses (spec A-20), partly entered spaces. */
    private static List<ChecklistWarning> warnings(PartsContent parts, SafetyContent safety,
                                                   List<EarlierHazard> earlier) {
        List<ChecklistWarning> warnings = new ArrayList<>();
        if (safety.hazards().isValue()) {
            for (Hazard hazard : safety.hazards().value()) {
                if (EarlierHazardChain.warns(hazard)) {
                    warnings.add(ChecklistWarning.openHazard(hazard.hazardId()));
                }
            }
        }
        for (EarlierHazard hazard : earlier) {
            if (hazard.addressedBy().isEmpty()) {
                warnings.add(ChecklistWarning.earlierHazard(hazard.checklistId(), hazard.checklistNo(),
                        hazard.hazard().hazardId()));
            }
        }
        for (PartsSpace space : parts.spaces()) {
            if (space.actualAccess().isValue() && space.actualAccess().value().extent() == AccessExtent.ENTERED_PART) {
                warnings.add(ChecklistWarning.partialAccess(space.spaceId()));
            }
        }
        return List.copyOf(warnings);
    }

    private static Object read(Map<ChecklistGroup, GroupDocument> groups, ChecklistGroup group,
                               IdentificationContent g1) {
        return InputForm.ofStored(groups.get(group), g1);
    }

    /** Collects missing items; a direct check (spec A-28) counts only with no owner source. */
    private static final class Missing {

        private final List<MissingItem> items = new ArrayList<>();

        void add(ChecklistGroup group, String spaceId, String field, MissingProblem problem) {
            items.add(new MissingItem(group, spaceId, field, problem));
        }

        void field(ChecklistGroup group, String spaceId, String field, Field<?> value) {
            if (value == null || value.state() == FieldState.UNKNOWN) {
                add(group, spaceId, field, MissingProblem.NOT_CHECKED);
            }
        }

        void sourced(ChecklistGroup group, String spaceId, String field, SourcedField<?> value) {
            boolean ownerWords = value != null && value.source() != null
                    && value.source().kind() != SourceKind.ON_SITE;
            if (value == null || value.isUnknown()
                    || ownerWords && DirectCheckCatalogue.contains(group.pathName(), field)) {
                add(group, spaceId, field, MissingProblem.NOT_CHECKED);
            }
        }

        /** BR-17: an area not or only partly entered stays missing whatever note it has. */
        void areaCheck(String spaceId, String field, Field<AreaCheck> check) {
            if (check != null && check.isValue() && check.value().result() == AreaCheckResult.NOT_ENTERED) {
                add(ChecklistGroup.CONDITION, spaceId, field, MissingProblem.REQUIRED_AREA_NOT_ENTERED);
            } else {
                field(ChecklistGroup.CONDITION, spaceId, field, check);
            }
        }
    }
}
