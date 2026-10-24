package vn.sunvibe.survey.core.domain.model.checklist.unit;

import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The provenance units of a group's content, in content order, each with its JSON path in that content
 * (survey-checklist spec 4.5, A-19, A-26; plan Task A6 key table). Authored notes and the rental scope are plain
 * units. Units the content does not hold (shared wet areas or charge overrides outside PARTS) are absent.
 */
public final class ChecklistUnits {

    private ChecklistUnits() {
    }

    /** A unit and its JSON path in the content it was read from. */
    public record Located(ProvenanceUnit unit, String path) {
    }

    /** The value an other item records for the correction rule: label, space and presence (spec A-19). */
    public record OtherItemValue(String label, String spaceId, YesNo presence) {

        @Override
        public String toString() {
            return "OtherItemValue[" + presence + "]";
        }
    }

    /** What the hazard list wrapper records: its state and whether the list is empty ("none seen"). */
    public record HazardListValue(FieldState state, Boolean empty, String naReason) {
    }

    /** The units keyed by unit key, in content order. */
    public static LinkedHashMap<UnitKey, ProvenanceUnit> of(GroupContent content) {
        LinkedHashMap<UnitKey, ProvenanceUnit> units = new LinkedHashMap<>();
        for (Located located : walk(content)) {
            units.put(located.unit().key(), located.unit());
        }
        return units;
    }

    /** The units with their paths, in content order. */
    public static List<Located> walk(GroupContent content) {
        Walker walker = new Walker();
        switch (content) {
            case IdentificationContent g -> walker.identification(g);
            case PartsContent g -> walker.parts(g);
            case DimensionsContent g -> walker.dimensions(g);
            case ConditionContent g -> walker.condition(g);
            case SafetyContent g -> walker.safety(g);
            case AmenitiesContent g -> walker.amenities(g);
            case TermsContent g -> walker.terms(g);
        }
        return walker.units;
    }

    private static final class Walker {

        private final List<Located> units = new ArrayList<>();

        void identification(IdentificationContent g) {
            ChecklistGroup group = ChecklistGroup.IDENTIFICATION;
            sourced(UnitKey.field(group, "observedAddress"), UnitKind.FIELD, g.observedAddress(),
                    "content.observedAddress");
            plain(UnitKey.field(group, "differenceNote"), UnitKind.NOTE, g.differenceNote(), "content.differenceNote");
            field(UnitKey.field(group, "propertyType"), g.propertyType(), "content.propertyType");
            field(UnitKey.field(group, "fit"), g.fit(), "content.fit");
            plain(UnitKey.field(group, "rentalScope"), UnitKind.FIELD, g.rentalScope(), "content.rentalScope");
            List<ObservedSpace> spaces = g.spaces() == null ? List.of() : g.spaces();
            for (int i = 0; i < spaces.size(); i++) {
                ObservedSpace space = spaces.get(i);
                add(new ProvenanceUnit(UnitKey.space(space.spaceId()), UnitKind.SPACE, FieldState.VALUE, space, null,
                        null, null, null, null, space), "content.spaces." + i);
            }
            plain(UnitKey.field(group, "otherPartsNote"), UnitKind.NOTE, g.otherPartsNote(), "content.otherPartsNote");
            plain(UnitKey.field(group, "generalNote"), UnitKind.NOTE, g.generalNote(), "content.generalNote");
        }

        void parts(PartsContent g) {
            ChecklistGroup group = ChecklistGroup.PARTS;
            field(UnitKey.field(group, "accessRoute"), g.accessRoute(), "content.accessRoute");
            field(UnitKey.field(group, "sharedAreas"), g.sharedAreas(), "content.sharedAreas");
            sourced(UnitKey.field(group, "totalFloors"), UnitKind.FIELD, g.totalFloors(), "content.totalFloors");
            List<PartsSpace> spaces = g.spaces() == null ? List.of() : g.spaces();
            for (int i = 0; i < spaces.size(); i++) {
                PartsSpace space = spaces.get(i);
                String at = "content.spaces." + i;
                field(UnitKey.spaceField(group, space.spaceId(), "floor"), space.floor(), at + ".floor");
                field(UnitKey.spaceField(group, space.spaceId(), "privateAreas"), space.privateAreas(),
                        at + ".privateAreas");
                field(UnitKey.spaceField(group, space.spaceId(), "actualAccess"), space.actualAccess(),
                        at + ".actualAccess");
            }
        }

        void dimensions(DimensionsContent g) {
            ChecklistGroup group = ChecklistGroup.DIMENSIONS;
            List<DimensionsSpace> spaces = g.spaces() == null ? List.of() : g.spaces();
            for (int i = 0; i < spaces.size(); i++) {
                DimensionsSpace space = spaces.get(i);
                String at = "content.spaces." + i;
                String id = space.spaceId();
                sourced(UnitKey.spaceField(group, id, "area"), UnitKind.FIELD, space.area(), at + ".area");
                sourced(UnitKey.spaceField(group, id, "bedrooms"), UnitKind.FIELD, space.bedrooms(), at + ".bedrooms");
                sourced(UnitKey.spaceField(group, id, "privateBathrooms"), UnitKind.FIELD, space.privateBathrooms(),
                        at + ".privateBathrooms");
                sourced(UnitKey.spaceField(group, id, "sharedBathroomUse"), UnitKind.FIELD, space.sharedBathroomUse(),
                        at + ".sharedBathroomUse");
                plain(UnitKey.spaceField(group, id, "layoutNote"), UnitKind.NOTE, space.layoutNote(),
                        at + ".layoutNote");
            }
        }

        void condition(ConditionContent g) {
            ChecklistGroup group = ChecklistGroup.CONDITION;
            if (g.sharedKitchen() != null) {
                field(UnitKey.field(group, "sharedKitchen"), g.sharedKitchen(), "content.sharedKitchen");
            }
            if (g.sharedBathroom() != null) {
                field(UnitKey.field(group, "sharedBathroom"), g.sharedBathroom(), "content.sharedBathroom");
            }
            List<ConditionSpace> spaces = g.spaces() == null ? List.of() : g.spaces();
            for (int i = 0; i < spaces.size(); i++) {
                ConditionSpace space = spaces.get(i);
                String at = "content.spaces." + i;
                String id = space.spaceId();
                field(UnitKey.spaceField(group, id, "occupancy"), space.occupancy(), at + ".occupancy");
                sourced(UnitKey.spaceField(group, id, "expectedVacantFrom"), UnitKind.FIELD,
                        space.expectedVacantFrom(), at + ".expectedVacantFrom");
                sourced(UnitKey.spaceField(group, id, "availableFrom"), UnitKind.FIELD, space.availableFrom(),
                        at + ".availableFrom");
                field(UnitKey.spaceField(group, id, "mainSpace"), space.mainSpace(), at + ".mainSpace");
                field(UnitKey.spaceField(group, id, "kitchen"), space.kitchen(), at + ".kitchen");
                field(UnitKey.spaceField(group, id, "bathroom"), space.bathroom(), at + ".bathroom");
            }
        }

        void safety(SafetyContent g) {
            ChecklistGroup group = ChecklistGroup.SAFETY;
            field(UnitKey.field(group, "egress"), g.egress(), "content.egress");
            field(UnitKey.field(group, "accessRestrictions"), g.accessRestrictions(), "content.accessRestrictions");
            Field<List<Hazard>> list = g.hazards();
            if (list == null) {
                return;
            }
            HazardListValue listValue = new HazardListValue(list.state(),
                    list.value() == null ? null : list.value().isEmpty(), list.naReason());
            add(new ProvenanceUnit(UnitKey.hazardList(), UnitKind.HAZARD_LIST, list.state(), listValue, null, null,
                    null, null, null, listValue), "content.hazards");
            List<Hazard> hazards = list.value() == null ? List.of() : list.value();
            for (int i = 0; i < hazards.size(); i++) {
                Hazard hazard = hazards.get(i);
                OwnerReport report = hazard.status() == HazardStatus.OWNER_REPORTED_FIXED
                        ? new OwnerReport(hazard.ownerReportedOn(), hazard.receivedByUserId(), hazard.relayBasis(),
                        hazard.statusNote())
                        : null;
                add(new ProvenanceUnit(UnitKey.hazard(hazard.hazardId()), UnitKind.HAZARD, FieldState.VALUE,
                        report == null ? null : report.statusNote(), null, null, null, null, report, hazard),
                        "content.hazards.value." + i);
            }
        }

        void amenities(AmenitiesContent g) {
            List<AmenityItem> shared = g.sharedItems() == null ? List.of() : g.sharedItems();
            for (int i = 0; i < shared.size(); i++) {
                item(UnitKey.sharedItem(shared.get(i).code()), shared.get(i), "content.sharedItems." + i);
            }
            List<AmenitiesSpace> spaces = g.spaces() == null ? List.of() : g.spaces();
            for (int i = 0; i < spaces.size(); i++) {
                AmenitiesSpace space = spaces.get(i);
                String at = "content.spaces." + i;
                sourced(UnitKey.spaceField(ChecklistGroup.AMENITIES, space.spaceId(), "furnishing"), UnitKind.FIELD,
                        space.furnishing(), at + ".furnishing");
                List<AmenityItem> items = space.items() == null ? List.of() : space.items();
                for (int j = 0; j < items.size(); j++) {
                    item(UnitKey.spaceItem(space.spaceId(), items.get(j).code()), items.get(j),
                            at + ".items." + j);
                }
            }
            List<OtherItem> others = g.otherItems() == null ? List.of() : g.otherItems();
            for (int i = 0; i < others.size(); i++) {
                OtherItem item = others.get(i);
                add(new ProvenanceUnit(UnitKey.otherItem(item.itemId()), UnitKind.ITEM, FieldState.VALUE,
                        new OtherItemValue(item.label(), item.spaceId(), item.presence()), item.source(),
                        item.confirmedOn(), item.receivedByUserId(), item.relayBasis(), null, item),
                        "content.otherItems." + i);
            }
        }

        void terms(TermsContent g) {
            TermsCommon common = g.common();
            if (common != null) {
                String at = "content.common.";
                sourced(UnitKey.commonTerm("electricity"), UnitKind.FIELD, common.electricity(), at + "electricity");
                sourced(UnitKey.commonTerm("water"), UnitKind.FIELD, common.water(), at + "water");
                sourced(UnitKey.commonTerm("internet"), UnitKind.FIELD, common.internet(), at + "internet");
                sourced(UnitKey.commonTerm("parking"), UnitKind.FIELD, common.parking(), at + "parking");
                sourced(UnitKey.commonTerm("serviceFee"), UnitKind.FIELD, common.serviceFee(), at + "serviceFee");
                sourced(UnitKey.commonTerm("otherFees"), UnitKind.FIELD, common.otherFees(), at + "otherFees");
                sourced(UnitKey.commonTerm("houseRules"), UnitKind.FIELD, common.houseRules(), at + "houseRules");
                sourced(UnitKey.commonTerm("pets"), UnitKind.FIELD, common.pets(), at + "pets");
                sourced(UnitKey.commonTerm("handoverMaintenance"), UnitKind.FIELD, common.handoverMaintenance(),
                        at + "handoverMaintenance");
            }
            List<TermsSpace> spaces = g.spaces() == null ? List.of() : g.spaces();
            ChecklistGroup group = ChecklistGroup.TERMS;
            for (int i = 0; i < spaces.size(); i++) {
                TermsSpace space = spaces.get(i);
                String at = "content.spaces." + i;
                String id = space.spaceId();
                sourced(UnitKey.spaceField(group, id, "monthlyRent"), UnitKind.FIELD, space.monthlyRent(),
                        at + ".monthlyRent");
                sourced(UnitKey.spaceField(group, id, "deposit"), UnitKind.FIELD, space.deposit(), at + ".deposit");
                sourced(UnitKey.spaceField(group, id, "paymentPeriodMonths"), UnitKind.FIELD,
                        space.paymentPeriodMonths(), at + ".paymentPeriodMonths");
                sourced(UnitKey.spaceField(group, id, "minimumTermMonths"), UnitKind.FIELD,
                        space.minimumTermMonths(), at + ".minimumTermMonths");
                ChargeOverrides overrides = space.chargeOverrides();
                if (overrides != null) {
                    String path = at + ".chargeOverrides.";
                    nullable(UnitKey.override(id, "electricity"), UnitKind.OVERRIDE, overrides.electricity(),
                            path + "electricity");
                    nullable(UnitKey.override(id, "water"), UnitKind.OVERRIDE, overrides.water(), path + "water");
                    nullable(UnitKey.override(id, "internet"), UnitKind.OVERRIDE, overrides.internet(),
                            path + "internet");
                    nullable(UnitKey.override(id, "parking"), UnitKind.OVERRIDE, overrides.parking(),
                            path + "parking");
                    nullable(UnitKey.override(id, "serviceFee"), UnitKind.OVERRIDE, overrides.serviceFee(),
                            path + "serviceFee");
                }
                // spec PD-4, PD-5: space fees are a unit of PARTS entries, which always carry the overrides object
                if (overrides != null || space.spaceFees() != null) {
                    nullable(UnitKey.spaceField(group, id, "spaceFees"), UnitKind.SPACE_FEES, space.spaceFees(),
                            at + ".spaceFees");
                }
            }
        }

        private void item(UnitKey key, AmenityItem item, String path) {
            add(new ProvenanceUnit(key, UnitKind.ITEM, FieldState.VALUE, item.presence(), item.source(),
                    item.confirmedOn(), item.receivedByUserId(), item.relayBasis(), null, item), path);
        }

        private void field(UnitKey key, Field<?> field, String path) {
            if (field != null) {
                add(new ProvenanceUnit(key, UnitKind.FIELD, field.state(), field.value(), null, null, null, null,
                        null, field), path);
            }
        }

        private void sourced(UnitKey key, UnitKind kind, SourcedField<?> field, String path) {
            if (field != null) {
                add(new ProvenanceUnit(key, kind, field.state(), field.value(), field.source(), field.confirmedOn(),
                        field.receivedByUserId(), field.relayBasis(), null, field), path);
            }
        }

        /** An override member or space fees: null means "the common value", read as an UNKNOWN unit. */
        private void nullable(UnitKey key, UnitKind kind, SourcedField<?> field, String path) {
            if (field == null) {
                add(new ProvenanceUnit(key, kind, FieldState.UNKNOWN, null, null, null, null, null, null, null), path);
            } else {
                sourced(key, kind, field, path);
            }
        }

        /** A plain nullable field: a value is VALUE, null is UNKNOWN (spec A-26). */
        private void plain(UnitKey key, UnitKind kind, Object value, String path) {
            add(new ProvenanceUnit(key, kind, value == null ? FieldState.UNKNOWN : FieldState.VALUE, value, null, null,
                    null, null, null, value), path);
        }

        private void add(ProvenanceUnit unit, String path) {
            units.add(new Located(unit, path));
        }
    }
}
