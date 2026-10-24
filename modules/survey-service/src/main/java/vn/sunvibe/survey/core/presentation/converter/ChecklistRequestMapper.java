package vn.sunvibe.survey.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.command.BasisInput;
import vn.sunvibe.survey.core.application.command.CorrectionInput;
import vn.sunvibe.survey.core.application.command.IntakeInput;
import vn.sunvibe.survey.core.application.command.SaveChecklistGroupCommand;
import vn.sunvibe.survey.core.application.command.StartChecklistCommand;
import vn.sunvibe.survey.core.application.command.TakeOverChecklistCommand;
import vn.sunvibe.survey.core.domain.model.checklist.ActualAccess;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Deposit;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;
import vn.sunvibe.survey.core.domain.model.checklist.FitAssessment;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.Observation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Pets;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistActualAccessFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistAmenitiesInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistAmenitiesSpaceInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistAmenityItemInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistAreaCheckFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistAreaFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistBasisRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistChargeOverridesInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistChargeTermInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistConditionInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistConditionSpaceInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistCorrectionRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistCountFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistDateTermInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistDepositTermInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistDimensionsInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistDimensionsSpaceInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistFeeListTermInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistFitFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistFurnishingFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistHazardInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistHazardListFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistIdentificationInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistIntakeRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistLocationFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistMonthsTermInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistObservationFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistObservedSpaceInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistOccupancyFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistOtherGroupVersions;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistOtherItemInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistPartsInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistPartsSpaceInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistPetsTermInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistPropertyTypeFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistRentTermInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSafetyInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveAmenitiesRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveConditionRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveDimensionsRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveIdentificationRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSavePartsRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveSafetyRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveTermsRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistStartRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistTakeoverRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistTermsCommonInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistTermsInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistTermsSpaceInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistTextFieldInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistTextTermInput;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistYesNoCountFieldInput;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistActualAccess;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAreaCheck;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCharge;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDeposit;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistFee;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistFit;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistHazardLink;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistMoney;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistObservation;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistObservedLocation;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPets;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRentMoney;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Converter - ChecklistRequestMapper
 * <p>
 * Turns the checklist request bodies into commands as sent: every wrapper, value and list keeps the nulls the client
 * sent, so the template rules report each failure on its JSON path (survey-checklist spec 5.5, plan PD-2); nothing is
 * checked or dropped here. A family source enum becomes the one domain source of the same name.
 */
@Component
public class ChecklistRequestMapper {

    public StartChecklistCommand toCommand(String appointmentId, ChecklistStartRequest request) {
        ChecklistIntakeRequest intake = request.intake();
        return new StartChecklistCommand(appointmentId,
                intake == null ? null : new IntakeInput(intake.reason(), basis(intake.basis())));
    }

    public TakeOverChecklistCommand toCommand(String checklistId, ChecklistTakeoverRequest request) {
        return new TakeOverChecklistCommand(checklistId, request.expectedVersion(), request.reason(),
                basis(request.basis()));
    }

    // ==================== group saves ====================

    public SaveChecklistGroupCommand toCommand(String checklistId, ChecklistSaveIdentificationRequest request) {
        return new SaveChecklistGroupCommand(checklistId, ChecklistGroup.IDENTIFICATION, request.expectedVersion(),
                otherVersions(request.otherGroupVersions()), correction(request.correction()),
                identification(request.content()));
    }

    public SaveChecklistGroupCommand toCommand(String checklistId, ChecklistSavePartsRequest request) {
        return save(checklistId, ChecklistGroup.PARTS, request.expectedVersion(), request.correction(),
                parts(request.content()));
    }

    public SaveChecklistGroupCommand toCommand(String checklistId, ChecklistSaveDimensionsRequest request) {
        return save(checklistId, ChecklistGroup.DIMENSIONS, request.expectedVersion(), request.correction(),
                dimensions(request.content()));
    }

    public SaveChecklistGroupCommand toCommand(String checklistId, ChecklistSaveConditionRequest request) {
        return save(checklistId, ChecklistGroup.CONDITION, request.expectedVersion(), request.correction(),
                condition(request.content()));
    }

    public SaveChecklistGroupCommand toCommand(String checklistId, ChecklistSaveSafetyRequest request) {
        return save(checklistId, ChecklistGroup.SAFETY, request.expectedVersion(), request.correction(),
                safety(request.content()));
    }

    public SaveChecklistGroupCommand toCommand(String checklistId, ChecklistSaveAmenitiesRequest request) {
        return save(checklistId, ChecklistGroup.AMENITIES, request.expectedVersion(), request.correction(),
                amenities(request.content()));
    }

    public SaveChecklistGroupCommand toCommand(String checklistId, ChecklistSaveTermsRequest request) {
        return save(checklistId, ChecklistGroup.TERMS, request.expectedVersion(), request.correction(),
                terms(request.content()));
    }

    private static SaveChecklistGroupCommand save(String checklistId, ChecklistGroup group, Long expectedVersion,
                                                  ChecklistCorrectionRequest correction, GroupContent content) {
        return new SaveChecklistGroupCommand(checklistId, group, expectedVersion, Map.of(), correction(correction),
                content);
    }

    /** spec A-12: the versions sent for the groups a G1 save may cascade into; a left-out group is absent. */
    private static Map<ChecklistGroup, Long> otherVersions(ChecklistOtherGroupVersions versions) {
        Map<ChecklistGroup, Long> map = new EnumMap<>(ChecklistGroup.class);
        if (versions != null) {
            putIfSent(map, ChecklistGroup.PARTS, versions.parts());
            putIfSent(map, ChecklistGroup.DIMENSIONS, versions.dimensions());
            putIfSent(map, ChecklistGroup.CONDITION, versions.condition());
            putIfSent(map, ChecklistGroup.SAFETY, versions.safety());
            putIfSent(map, ChecklistGroup.AMENITIES, versions.amenities());
            putIfSent(map, ChecklistGroup.TERMS, versions.terms());
        }
        return map;
    }

    private static void putIfSent(Map<ChecklistGroup, Long> map, ChecklistGroup group, Long version) {
        if (version != null) {
            map.put(group, version);
        }
    }

    private static CorrectionInput correction(ChecklistCorrectionRequest correction) {
        return correction == null ? null : new CorrectionInput(correction.reason(), basis(correction.basis()));
    }

    static BasisInput basis(ChecklistBasisRequest basis) {
        return basis == null ? null : new BasisInput(basis.confirmedOn(), basis.text());
    }

    // ==================== group contents ====================

    private static IdentificationContent identification(ChecklistIdentificationInput g) {
        if (g == null) {
            return null;
        }
        ChecklistLocationFieldInput address = g.observedAddress();
        ChecklistPropertyTypeFieldInput type = g.propertyType();
        ChecklistFitFieldInput fit = g.fit();
        return new IdentificationContent(
                address == null ? null : sourced(address.state(), location(address.value()), address.naReason(),
                        address.source(), address.confirmedOn(), address.receivedByUserId(), address.relayBasis()),
                g.differenceNote(),
                type == null ? null : new Field<>(type.state(), type.value(), type.naReason()),
                fit == null ? null : new Field<>(fit.state(), fit(fit.value()), fit.naReason()),
                g.rentalScope(), list(g.spaces(), ChecklistRequestMapper::space), g.otherPartsNote(),
                g.generalNote());
    }

    private static PartsContent parts(ChecklistPartsInput g) {
        return g == null ? null : new PartsContent(text(g.accessRoute()), text(g.sharedAreas()),
                count(g.totalFloors()), list(g.spaces(), ChecklistRequestMapper::partsSpace));
    }

    private static DimensionsContent dimensions(ChecklistDimensionsInput g) {
        return g == null ? null : new DimensionsContent(list(g.spaces(), ChecklistRequestMapper::dimensionsSpace));
    }

    private static ConditionContent condition(ChecklistConditionInput g) {
        return g == null ? null : new ConditionContent(areaCheck(g.sharedKitchen()), areaCheck(g.sharedBathroom()),
                list(g.spaces(), ChecklistRequestMapper::conditionSpace));
    }

    private static SafetyContent safety(ChecklistSafetyInput g) {
        if (g == null) {
            return null;
        }
        ChecklistObservationFieldInput restrictions = g.accessRestrictions();
        ChecklistHazardListFieldInput hazards = g.hazards();
        return new SafetyContent(text(g.egress()),
                restrictions == null ? null : new Field<>(restrictions.state(), observation(restrictions.value()),
                        restrictions.naReason()),
                hazards == null ? null : new Field<>(hazards.state(),
                        list(hazards.value(), ChecklistRequestMapper::hazard), hazards.naReason()));
    }

    private static AmenitiesContent amenities(ChecklistAmenitiesInput g) {
        return g == null ? null : new AmenitiesContent(list(g.sharedItems(), ChecklistRequestMapper::item),
                list(g.spaces(), ChecklistRequestMapper::amenitiesSpace),
                list(g.otherItems(), ChecklistRequestMapper::otherItem));
    }

    private static TermsContent terms(ChecklistTermsInput g) {
        if (g == null) {
            return null;
        }
        ChecklistTermsCommonInput c = g.common();
        TermsCommon common = c == null ? null : new TermsCommon(charge(c.electricity()), charge(c.water()),
                charge(c.internet()), charge(c.parking()), charge(c.serviceFee()), fees(c.otherFees()),
                textTerm(c.houseRules()), pets(c.pets()), textTerm(c.handoverMaintenance()));
        return new TermsContent(common, list(g.spaces(), ChecklistRequestMapper::termsSpace));
    }

    // ==================== entries ====================

    private static ObservedSpace space(ChecklistObservedSpaceInput space) {
        return new ObservedSpace(space.spaceId(), space.code(), space.location(), space.description());
    }

    private static PartsSpace partsSpace(ChecklistPartsSpaceInput space) {
        ChecklistActualAccessFieldInput access = space.actualAccess();
        return new PartsSpace(space.spaceId(), text(space.floor()), text(space.privateAreas()),
                access == null ? null : new Field<>(access.state(), actualAccess(access.value()), access.naReason()));
    }

    private static DimensionsSpace dimensionsSpace(ChecklistDimensionsSpaceInput space) {
        ChecklistAreaFieldInput area = space.area();
        ChecklistYesNoCountFieldInput shared = space.sharedBathroomUse();
        return new DimensionsSpace(space.spaceId(),
                area == null ? null : sourced(area.state(), area.value(), area.naReason(), area.source(),
                        area.confirmedOn(), area.receivedByUserId(), area.relayBasis()),
                count(space.bedrooms()), count(space.privateBathrooms()),
                shared == null ? null : sourced(shared.state(), shared.value(), shared.naReason(), shared.source(),
                        shared.confirmedOn(), shared.receivedByUserId(), shared.relayBasis()),
                space.layoutNote());
    }

    private static ConditionSpace conditionSpace(ChecklistConditionSpaceInput space) {
        ChecklistOccupancyFieldInput occupancy = space.occupancy();
        return new ConditionSpace(space.spaceId(),
                occupancy == null ? null : new Field<>(occupancy.state(), occupancy.value(), occupancy.naReason()),
                date(space.expectedVacantFrom()), date(space.availableFrom()), areaCheck(space.mainSpace()),
                areaCheck(space.kitchen()), areaCheck(space.bathroom()));
    }

    private static Hazard hazard(ChecklistHazardInput hazard) {
        ChecklistHazardLink previous = hazard.previous();
        return new Hazard(hazard.hazardId(), hazard.spaceId(),
                previous == null ? null : new HazardLink(previous.checklistId(), previous.hazardId()),
                hazard.description(), hazard.severity(), hazard.status(), hazard.statusNote(),
                hazard.ownerReportedOn(), hazard.receivedByUserId(), hazard.relayBasis());
    }

    private static AmenitiesSpace amenitiesSpace(ChecklistAmenitiesSpaceInput space) {
        ChecklistFurnishingFieldInput furnishing = space.furnishing();
        return new AmenitiesSpace(space.spaceId(),
                furnishing == null ? null : sourced(furnishing.state(), furnishing.value(), furnishing.naReason(),
                        furnishing.source(), furnishing.confirmedOn(), furnishing.receivedByUserId(),
                        furnishing.relayBasis()),
                list(space.items(), ChecklistRequestMapper::item));
    }

    private static AmenityItem item(ChecklistAmenityItemInput item) {
        return new AmenityItem(item.code(), item.presence(), source(item.source()), item.confirmedOn(),
                item.receivedByUserId(), item.relayBasis());
    }

    private static OtherItem otherItem(ChecklistOtherItemInput item) {
        return new OtherItem(item.itemId(), item.label(), item.spaceId(), item.presence(), source(item.source()),
                item.confirmedOn(), item.receivedByUserId(), item.relayBasis());
    }

    private static TermsSpace termsSpace(ChecklistTermsSpaceInput space) {
        ChecklistRentTermInput rent = space.monthlyRent();
        ChecklistDepositTermInput deposit = space.deposit();
        ChecklistChargeOverridesInput overrides = space.chargeOverrides();
        return new TermsSpace(space.spaceId(),
                rent == null ? null : sourced(rent.state(), rentMoney(rent.value()), rent.naReason(), rent.source(),
                        rent.confirmedOn(), rent.receivedByUserId(), rent.relayBasis()),
                deposit == null ? null : sourced(deposit.state(), deposit(deposit.value()), deposit.naReason(),
                        deposit.source(), deposit.confirmedOn(), deposit.receivedByUserId(), deposit.relayBasis()),
                months(space.paymentPeriodMonths()), months(space.minimumTermMonths()),
                overrides == null ? null : new ChargeOverrides(charge(overrides.electricity()),
                        charge(overrides.water()), charge(overrides.internet()), charge(overrides.parking()),
                        charge(overrides.serviceFee())),
                fees(space.spaceFees()));
    }

    // ==================== wrappers ====================

    private static Field<String> text(ChecklistTextFieldInput field) {
        return field == null ? null : new Field<>(field.state(), field.value(), field.naReason());
    }

    private static Field<AreaCheck> areaCheck(ChecklistAreaCheckFieldInput field) {
        return field == null ? null : new Field<>(field.state(), areaCheck(field.value()), field.naReason());
    }

    private static SourcedField<Integer> count(ChecklistCountFieldInput field) {
        return field == null ? null : sourced(field.state(), field.value(), field.naReason(), field.source(),
                field.confirmedOn(), field.receivedByUserId(), field.relayBasis());
    }

    private static SourcedField<LocalDate> date(ChecklistDateTermInput field) {
        return field == null ? null : sourced(field.state(), field.value(), field.naReason(), field.source(),
                field.confirmedOn(), field.receivedByUserId(), field.relayBasis());
    }

    private static SourcedField<Integer> months(ChecklistMonthsTermInput field) {
        return field == null ? null : sourced(field.state(), field.value(), field.naReason(), field.source(),
                field.confirmedOn(), field.receivedByUserId(), field.relayBasis());
    }

    private static SourcedField<Charge> charge(ChecklistChargeTermInput field) {
        return field == null ? null : sourced(field.state(), charge(field.value()), field.naReason(), field.source(),
                field.confirmedOn(), field.receivedByUserId(), field.relayBasis());
    }

    private static SourcedField<List<Fee>> fees(ChecklistFeeListTermInput field) {
        return field == null ? null : sourced(field.state(), list(field.value(), ChecklistRequestMapper::fee),
                field.naReason(), field.source(), field.confirmedOn(), field.receivedByUserId(), field.relayBasis());
    }

    private static SourcedField<String> textTerm(ChecklistTextTermInput field) {
        return field == null ? null : sourced(field.state(), field.value(), field.naReason(), field.source(),
                field.confirmedOn(), field.receivedByUserId(), field.relayBasis());
    }

    private static SourcedField<Pets> pets(ChecklistPetsTermInput field) {
        return field == null ? null : sourced(field.state(), pets(field.value()), field.naReason(), field.source(),
                field.confirmedOn(), field.receivedByUserId(), field.relayBasis());
    }

    private static <V> SourcedField<V> sourced(FieldState state, V value, String naReason, Enum<?> source,
                                               LocalDate confirmedOn, String receivedByUserId, String relayBasis) {
        return new SourcedField<>(state, value, naReason, source(source), confirmedOn, receivedByUserId, relayBasis);
    }

    /** The domain source of a family enum: the family enums of spec 7.2 use the domain names. */
    private static ChecklistSource source(Enum<?> family) {
        return family == null ? null : ChecklistSource.valueOf(family.name());
    }

    // ==================== values ====================

    /** The list as sent, a null element kept so the list rule reports it on its index. */
    private static <I, D> List<D> list(List<I> inputs, Function<I, D> convert) {
        if (inputs == null) {
            return null;
        }
        List<D> converted = new ArrayList<>(inputs.size());
        for (I input : inputs) {
            converted.add(input == null ? null : convert.apply(input));
        }
        return converted;
    }

    private static ObservedLocation location(ChecklistObservedLocation location) {
        return location == null ? null : new ObservedLocation(location.addressText(), location.provinceCode(),
                location.wardCode(), location.latitude(), location.longitude());
    }

    private static FitAssessment fit(ChecklistFit fit) {
        return fit == null ? null : new FitAssessment(fit.assessment(), fit.reason());
    }

    private static ActualAccess actualAccess(ChecklistActualAccess access) {
        return access == null ? null : new ActualAccess(access.extent(), access.note());
    }

    private static Observation observation(ChecklistObservation observation) {
        return observation == null ? null : new Observation(observation.noneObserved(), observation.note());
    }

    private static AreaCheck areaCheck(ChecklistAreaCheck check) {
        return check == null ? null : new AreaCheck(check.result(), check.noneObserved(), check.note(),
                check.notEnteredReason());
    }

    private static Money money(ChecklistMoney money) {
        return money == null ? null : new Money(money.amount(), money.currency());
    }

    private static RentMoney rentMoney(ChecklistRentMoney rent) {
        return rent == null ? null : new RentMoney(rent.amount(), rent.currency(), rent.unit());
    }

    private static Deposit deposit(ChecklistDeposit deposit) {
        return deposit == null ? null : new Deposit(deposit.basis(), deposit.months(), money(deposit.amount()));
    }

    private static Charge charge(ChecklistCharge charge) {
        return charge == null ? null : new Charge(charge.basis(), money(charge.amount()), charge.chargeUnit(),
                charge.note());
    }

    private static Fee fee(ChecklistFee fee) {
        return new Fee(fee.name(), money(fee.amount()), fee.chargeUnit());
    }

    private static Pets pets(ChecklistPets pets) {
        return pets == null ? null : new Pets(pets.policy(), pets.note());
    }
}
