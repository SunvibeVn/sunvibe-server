package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeBasis;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.Deposit;
import vn.sunvibe.survey.core.domain.model.checklist.DepositBasis;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.Pets;
import vn.sunvibe.survey.core.domain.model.checklist.PetsPolicy;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.SourceFamily;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/** The body rules of G6 and G8 (survey-checklist spec 4.6; BR-5, BR-11 G6) with the money of spec D-1. */
final class AmenitiesTermsRules {

    static final int MAX_OTHER_ITEMS = 20;
    static final int MAX_FEES = 10;

    private AmenitiesTermsRules() {
    }

    // ==================== G6 amenities ====================

    static AmenitiesContent amenities(BodyChecker check, AmenitiesContent g) {
        List<AmenityItem> sharedItems = items(check, "content.sharedItems", g.sharedItems());
        List<AmenitiesSpace> spaces = TemplateV1Rules.entries(check, g.spaces(), AmenitiesSpace::spaceId,
                (at, space) -> new AmenitiesSpace(space.spaceId(),
                        check.sourced(at + ".furnishing", space.furnishing(), SourceFamily.AMENITY, false, false,
                                (path, value) -> value),
                        items(check, at + ".items", space.items())));
        List<OtherItem> otherItems = otherItems(check, g.otherItems());
        return new AmenitiesContent(sharedItems, spaces, otherItems);
    }

    /** The checked items of one level: a v1 code at most once, present or not, with an amenity source. */
    private static List<AmenityItem> items(BodyChecker check, String path, List<AmenityItem> items) {
        if (check.list(path, items, AmenityCode.values().length, "items") == null) {
            return null;
        }
        Set<AmenityCode> codes = EnumSet.noneOf(AmenityCode.class);
        List<AmenityItem> normalised = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            AmenityItem item = items.get(i);
            String at = path + "." + i;
            AmenityCode code = check.present(at + ".code", item.code());
            if (code != null && !codes.add(code)) {
                check.add(at + ".code", at + ".code is already listed at this level.");
            }
            check.present(at + ".presence", item.presence());
            ItemSource source = itemSource(check, at, item.source(), item.confirmedOn(), item.receivedByUserId(),
                    item.relayBasis());
            normalised.add(new AmenityItem(code, item.presence(), source.source(), source.statement().confirmedOn(),
                    source.statement().receivedByUserId(), source.statement().relayBasis()));
        }
        return normalised;
    }

    /** survey-checklist spec 4.6 G6: at most 20 other items with a client ULID, a label and an optional space. */
    private static List<OtherItem> otherItems(BodyChecker check, List<OtherItem> items) {
        String path = "content.otherItems";
        if (check.list(path, items, MAX_OTHER_ITEMS, "other items") == null) {
            return null;
        }
        Set<String> ids = new HashSet<>();
        List<OtherItem> normalised = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            OtherItem item = items.get(i);
            String at = path + "." + i;
            String id = TemplateV1Rules.uniqueId(check, at + ".itemId", item.itemId(), ids);
            String label = check.text(at + ".label", item.label(), 100);
            String spaceId = item.spaceId() == null ? null : check.ulid(at + ".spaceId", item.spaceId());
            check.present(at + ".presence", item.presence());
            ItemSource source = itemSource(check, at, item.source(), item.confirmedOn(), item.receivedByUserId(),
                    item.relayBasis());
            normalised.add(new OtherItem(id, label, spaceId, item.presence(), source.source(),
                    source.statement().confirmedOn(), source.statement().receivedByUserId(),
                    source.statement().relayBasis()));
        }
        return normalised;
    }

    private static ItemSource itemSource(BodyChecker check, String path, ChecklistSource source,
                                         LocalDate confirmedOn, String receivedByUserId, String relayBasis) {
        ChecklistSource checked = check.source(path, source, SourceFamily.AMENITY, false);
        if (checked == ChecklistSource.OWNER_STATED) {
            return new ItemSource(checked, check.statement(path, confirmedOn, receivedByUserId, relayBasis));
        }
        check.noStatement(path, confirmedOn, receivedByUserId, relayBasis, "unless the owner said it");
        return new ItemSource(checked, new BodyChecker.Statement(null, null, null));
    }

    private record ItemSource(ChecklistSource source, BodyChecker.Statement statement) {
    }

    // ==================== G8 terms ====================

    static TermsContent terms(BodyChecker check, TermsContent g) {
        TermsCommon common = g.common() == null ? null : common(check, "content.common", g.common());
        if (g.common() == null) {
            check.required("content.common");
        }
        List<TermsSpace> spaces = TemplateV1Rules.entries(check, g.spaces(), TermsSpace::spaceId,
                (at, space) -> new TermsSpace(space.spaceId(),
                        term(check, at + ".monthlyRent", space.monthlyRent(), true,
                                (path, value) -> rent(check, path, value)),
                        term(check, at + ".deposit", space.deposit(), false,
                                (path, value) -> deposit(check, path, value)),
                        term(check, at + ".paymentPeriodMonths", space.paymentPeriodMonths(), false,
                                (path, value) -> check.count(path, value, 1, 12)),
                        term(check, at + ".minimumTermMonths", space.minimumTermMonths(), false,
                                (path, value) -> check.count(path, value, 1, 120)),
                        overrides(check, at + ".chargeOverrides", space.chargeOverrides()),
                        space.spaceFees() == null ? null
                                : valueOnly(check, at + ".spaceFees", feeList(check, at + ".spaceFees",
                                space.spaceFees()), space.spaceFees())));
        return new TermsContent(common, spaces);
    }

    private static TermsCommon common(BodyChecker check, String path, TermsCommon c) {
        return new TermsCommon(charge(check, path + ".electricity", c.electricity()),
                charge(check, path + ".water", c.water()), charge(check, path + ".internet", c.internet()),
                charge(check, path + ".parking", c.parking()), charge(check, path + ".serviceFee", c.serviceFee()),
                feeList(check, path + ".otherFees", c.otherFees()),
                term(check, path + ".houseRules", c.houseRules(), false, (p, value) -> check.text(p, value, 2000)),
                term(check, path + ".pets", c.pets(), false, (p, value) -> pets(check, p, value)),
                term(check, path + ".handoverMaintenance", c.handoverMaintenance(), false,
                        (p, value) -> check.text(p, value, 2000)));
    }

    /** A terms field: terms source, owner's form only on the monthly rent (spec A-18), no N/A. */
    private static <V> SourcedField<V> term(BodyChecker check, String path, SourcedField<V> field,
                                            boolean ownerFormAllowed, BiFunction<String, V, V> rule) {
        return check.sourced(path, field, SourceFamily.TERMS, ownerFormAllowed, false, rule);
    }

    private static SourcedField<Charge> charge(BodyChecker check, String path, SourcedField<Charge> field) {
        return term(check, path, field, false, (p, value) -> charge(check, p, value));
    }

    /** survey-checklist BR-5: the amount and its unit exactly for AMOUNT; an optional note of 1..200. */
    private static Charge charge(BodyChecker check, String path, Charge value) {
        ChargeBasis basis = check.present(path + ".basis", value.basis());
        Money amount = null;
        if (basis == ChargeBasis.AMOUNT) {
            amount = money(check, path + ".amount", value.amount());
            check.present(path + ".chargeUnit", value.chargeUnit());
        } else {
            if (value.amount() != null) {
                check.mustBeEmpty(path + ".amount", "unless the basis is AMOUNT");
            }
            if (value.chargeUnit() != null) {
                check.mustBeEmpty(path + ".chargeUnit", "unless the basis is AMOUNT");
            }
        }
        return new Charge(basis, amount, basis == ChargeBasis.AMOUNT ? value.chargeUnit() : null,
                check.optionalText(path + ".note", value.note(), 200));
    }

    /** spec D-1: whole VND from 0 to 999,999,999,999,999; 0 means confirmed free or no deposit. */
    private static Money money(BodyChecker check, String path, Money value) {
        if (value == null) {
            check.required(path);
            return null;
        }
        amount(check, path + ".amount", value.amount(), 0);
        currency(check, path + ".currency", value.currency());
        return value;
    }

    /** spec D-1: a monthly rent of at least 1 VND, in the ADR-006 price shape with unit MONTH. */
    private static RentMoney rent(BodyChecker check, String path, RentMoney value) {
        amount(check, path + ".amount", value.amount(), 1);
        currency(check, path + ".currency", value.currency());
        if (!RentMoney.MONTH.equals(value.unit())) {
            check.add(path + ".unit", path + ".unit must be MONTH.");
        }
        return value;
    }

    private static void amount(BodyChecker check, String path, Long amount, long min) {
        if (amount == null) {
            check.required(path);
        } else if (amount < min || amount > Money.MAX_AMOUNT) {
            check.add(path, path + " must be a whole number of VND from " + min + " to " + Money.MAX_AMOUNT + ".");
        }
    }

    private static void currency(BodyChecker check, String path, String currency) {
        if (!Money.VND.equals(currency)) {
            check.add(path, path + " must be VND.");
        }
    }

    /** survey-checklist BR-5, D-AI-24: months 0..12 or an amount, never both; 0 is no deposit. */
    private static Deposit deposit(BodyChecker check, String path, Deposit value) {
        DepositBasis basis = check.present(path + ".basis", value.basis());
        if (basis == DepositBasis.MONTHS) {
            if (value.amount() != null) {
                check.mustBeEmpty(path + ".amount", "unless the basis is AMOUNT");
            }
            return new Deposit(basis, check.count(path + ".months", value.months(), 0, 12), null);
        }
        if (basis == DepositBasis.AMOUNT) {
            if (value.months() != null) {
                check.mustBeEmpty(path + ".months", "unless the basis is MONTHS");
            }
            return new Deposit(basis, null, money(check, path + ".amount", value.amount()));
        }
        return value;
    }

    private static Pets pets(BodyChecker check, String path, Pets value) {
        PetsPolicy policy = check.present(path + ".policy", value.policy());
        String note = policy == PetsPolicy.CONDITIONAL
                ? check.text(path + ".note", value.note(), 500)
                : check.optionalText(path + ".note", value.note(), 500);
        return new Pets(policy, note);
    }

    private static SourcedField<List<Fee>> feeList(BodyChecker check, String path, SourcedField<List<Fee>> field) {
        return term(check, path, field, false, (p, fees) -> fees(check, p, fees));
    }

    /** survey-checklist BR-5: at most 10 fees, each with a name of 1..100, an amount and its unit. */
    private static List<Fee> fees(BodyChecker check, String path, List<Fee> fees) {
        if (check.list(path, fees, MAX_FEES, "fees") == null) {
            return null;
        }
        List<Fee> normalised = new ArrayList<>();
        for (int i = 0; i < fees.size(); i++) {
            Fee fee = fees.get(i);
            String at = path + "." + i;
            normalised.add(new Fee(check.text(at + ".name", fee.name(), 100), money(check, at + ".amount",
                    fee.amount()), check.present(at + ".chargeUnit", fee.chargeUnit())));
        }
        return normalised;
    }

    /**
     * spec 4.6: each member null (the common charge applies) or a charge term in VALUE; whether the scope allows
     * overrides at all is a rule of save step 9.
     */
    private static ChargeOverrides overrides(BodyChecker check, String path, ChargeOverrides value) {
        if (value == null) {
            return null;
        }
        return new ChargeOverrides(override(check, path + ".electricity", value.electricity()),
                override(check, path + ".water", value.water()), override(check, path + ".internet", value.internet()),
                override(check, path + ".parking", value.parking()),
                override(check, path + ".serviceFee", value.serviceFee()));
    }

    private static SourcedField<Charge> override(BodyChecker check, String path, SourcedField<Charge> member) {
        return member == null ? null : valueOnly(check, path, charge(check, path, member), member);
    }

    /** A term that may only be null or VALUE (overrides, space fees). */
    private static <V> SourcedField<V> valueOnly(BodyChecker check, String path, SourcedField<V> checked,
                                                 SourcedField<V> sent) {
        if (sent.state() != null && !sent.isValue()) {
            check.add(path + ".state", path + ".state must be VALUE; send null to keep the common value.");
        }
        return checked;
    }
}
