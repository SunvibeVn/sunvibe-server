package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ActualAccess;
import vn.sunvibe.survey.core.domain.model.checklist.AccessExtent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.FitAssessment;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourceFamily;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TemplateV1;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.shared.text.PhoneNumberDetector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The body rules of template v1 (survey-checklist spec 4.6, 5.5 save step 1) and the canonical form a save compares
 * with the stored content (spec PD-5). The one validator of checklist content: the content records accept any value
 * and every broken rule of a body is reported at once with its JSON path (spec PD-2). Rules that need stored data
 * (the catalog and pin through the lead port, unknown spaces, receivers, owner's form values, links to earlier
 * hazards, basis and fix-report dates) belong to save steps 1 and 9 of the application.
 */
public final class TemplateV1Rules {

    /** survey-checklist US-1 AC-3: a space code is 1..20 of these characters. */
    private static final Pattern SPACE_CODE = Pattern.compile("[A-Za-z0-9 ._/-]{1,20}");
    public static final int MAX_SPACES = 50;
    private static final BigDecimal MIN_AREA = new BigDecimal("0.1");
    private static final BigDecimal MAX_AREA = new BigDecimal("99999.9");

    private TemplateV1Rules() {
    }

    /**
     * The content cleaned and checked: text NFC and trimmed with blank optional text as null, areas at scale 1.
     *
     * @throws vn.sunvibe.survey.core.domain.exception.ChecklistValidationException with every violation of the body
     */
    public static GroupContent checkAndNormalise(GroupContent content, LocalDate today) {
        BodyChecker check = new BodyChecker(today);
        GroupContent normalised = switch (content) {
            case IdentificationContent g -> identification(check, g);
            case PartsContent g -> parts(check, g);
            case DimensionsContent g -> dimensions(check, g);
            case ConditionContent g -> ConditionSafetyRules.condition(check, g);
            case SafetyContent g -> ConditionSafetyRules.safety(check, g);
            case AmenitiesContent g -> AmenitiesTermsRules.amenities(check, g);
            case TermsContent g -> AmenitiesTermsRules.terms(check, g);
        };
        check.throwIfAny();
        return normalised;
    }

    /**
     * The canonical form of a normalised body against the G1 content (spec PD-5): per-space entries in G1 space order,
     * the scope's empty entry for each G1 space the body left out, then entries of spaces G1 does not know, in body
     * order (never dropped, so save step 9 can refuse them). The empty G8 entry of a PARTS draft has five null
     * charge overrides and no space fees; outside PARTS both are null. G6 items are in code order. Spaces, hazards,
     * other items and fees keep the user's order, so a reorder is a real save. Request nulls stay null.
     */
    public static GroupContent canonical(GroupContent normalised, IdentificationContent g1) {
        return switch (normalised) {
            case IdentificationContent g -> g;
            case PartsContent g -> new PartsContent(g.accessRoute(), g.sharedAreas(), g.totalFloors(),
                    perSpace(g.spaces(), PartsSpace::spaceId, g1, TemplateV1::emptyPartsSpace));
            case DimensionsContent g -> new DimensionsContent(
                    perSpace(g.spaces(), DimensionsSpace::spaceId, g1, TemplateV1::emptyDimensionsSpace));
            case ConditionContent g -> new ConditionContent(g.sharedKitchen(), g.sharedBathroom(),
                    perSpace(g.spaces(), ConditionSpace::spaceId, g1, TemplateV1::emptyConditionSpace));
            case SafetyContent g -> g;
            case AmenitiesContent g -> new AmenitiesContent(byCode(g.sharedItems()),
                    perSpace(g.spaces().stream().map(space -> new AmenitiesSpace(space.spaceId(), space.furnishing(),
                            byCode(space.items()))).toList(), AmenitiesSpace::spaceId, g1,
                            TemplateV1::emptyAmenitiesSpace), g.otherItems());
            case TermsContent g -> new TermsContent(g.common(), perSpace(g.spaces(), TermsSpace::spaceId, g1,
                    spaceId -> TemplateV1.emptyTermsSpace(spaceId, g1.rentalScope())));
        };
    }

    private static List<AmenityItem> byCode(List<AmenityItem> items) {
        return items.stream().sorted(Comparator.comparing(AmenityItem::code)).toList();
    }

    /** Entries in G1 order with empty ones for missing spaces, then unknown spaces in body order. */
    static <T> List<T> perSpace(List<T> entries, Function<T, String> idOf, IdentificationContent g1,
                                Function<String, T> empty) {
        Map<String, T> byId = new LinkedHashMap<>();
        for (T entry : entries) {
            byId.putIfAbsent(idOf.apply(entry), entry);
        }
        List<T> ordered = new ArrayList<>();
        for (ObservedSpace space : g1.spaces()) {
            T entry = byId.remove(space.spaceId());
            ordered.add(entry != null ? entry : empty.apply(space.spaceId()));
        }
        ordered.addAll(byId.values());
        return ordered;
    }

    // ==================== G1 identification ====================

    private static IdentificationContent identification(BodyChecker check, IdentificationContent g) {
        SourcedField<ObservedLocation> address = check.sourced("content.observedAddress", g.observedAddress(),
                SourceFamily.LOCATION, true, false, (path, value) -> location(check, path, value));
        String differenceNote = check.optionalText("content.differenceNote", g.differenceNote(), 500);
        var propertyType = check.field("content.propertyType", g.propertyType(), false, (path, value) -> value);
        Field<FitAssessment> fit = check.field("content.fit", g.fit(), false, (path, value) -> fit(check, path, value));
        List<ObservedSpace> spaces = spaces(check, g.rentalScope(), g.spaces());
        String otherPartsNote = check.optionalText("content.otherPartsNote", g.otherPartsNote(), 1000);
        String generalNote = check.optionalText("content.generalNote", g.generalNote(), 2000);
        return new IdentificationContent(address, differenceNote, propertyType, fit, g.rentalScope(), spaces,
                otherPartsNote, generalNote);
    }

    /** The shape of an observed address; catalog and pin are checked through the lead port at save step 1. */
    private static ObservedLocation location(BodyChecker check, String path, ObservedLocation value) {
        String addressText = check.text(path + ".addressText", value.addressText(), 255);
        if (value.provinceCode() == null || value.provinceCode().isBlank()) {
            check.required(path + ".provinceCode");
        }
        if (value.wardCode() == null || value.wardCode().isBlank()) {
            check.required(path + ".wardCode");
        }
        if (value.latitude() == null && value.longitude() != null) {
            check.add(path + ".latitude", path + ".latitude and longitude come together.");
        } else if (value.latitude() != null && value.longitude() == null) {
            check.add(path + ".longitude", path + ".latitude and longitude come together.");
        }
        return new ObservedLocation(addressText, value.provinceCode(), value.wardCode(), value.latitude(),
                value.longitude());
    }

    private static FitAssessment fit(BodyChecker check, String path, FitAssessment value) {
        check.present(path + ".assessment", value.assessment());
        return new FitAssessment(value.assessment(), check.text(path + ".reason", value.reason(), 1000));
    }

    /** survey-checklist US-1 AC-3, BR-3: WHOLE exactly one space, PARTS 1..50, ids and codes unique. */
    private static List<ObservedSpace> spaces(BodyChecker check, RentalScope scope, List<ObservedSpace> spaces) {
        String path = "content.spaces";
        if (check.list(path, spaces, MAX_SPACES, "spaces") == null) {
            return null;
        }
        if (scope == RentalScope.WHOLE && spaces.size() != 1) {
            check.add(path, path + " must hold exactly one space for the whole property.");
        } else if (scope == RentalScope.PARTS && spaces.isEmpty()) {
            check.add(path, path + " must hold at least one space for separate parts.");
        }
        Set<String> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        List<ObservedSpace> normalised = new ArrayList<>();
        for (int i = 0; i < spaces.size(); i++) {
            ObservedSpace space = spaces.get(i);
            String at = path + "." + i;
            String id = uniqueId(check, at + ".spaceId", space.spaceId(), ids);
            String code = code(check, at + ".code", space.code());
            if (code != null && !codes.add(code.toLowerCase(Locale.ROOT))) {
                check.add(at + ".code", at + ".code is already used by another space.");
            }
            normalised.add(new ObservedSpace(id, code, check.text(at + ".location", space.location(), 200),
                    check.optionalText(at + ".description", space.description(), 500)));
        }
        return normalised;
    }

    private static String code(BodyChecker check, String path, String value) {
        String code = value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        if (code == null || !SPACE_CODE.matcher(code).matches()) {
            check.add(path, path + " must be 1 to 20 of the letters A-Z, digits, space, '.', '_', '/' or '-'.");
            return null;
        }
        if (PhoneNumberDetector.containsVietnamesePhoneNumber(code)) {
            check.add(path, path + " must not contain a phone number.");
            return null;
        }
        return code;
    }

    /** A client ULID not used twice in the list (spec A-23). */
    static String uniqueId(BodyChecker check, String path, String value, Set<String> seen) {
        String id = check.ulid(path, value);
        if (id != null && !seen.add(id)) {
            check.add(path, path + " is already used in this list.");
            return null;
        }
        return id;
    }

    // ==================== G2 parts ====================

    private static PartsContent parts(BodyChecker check, PartsContent g) {
        Field<String> accessRoute = textField(check, "content.accessRoute", g.accessRoute(), 1000, false);
        Field<String> sharedAreas = textField(check, "content.sharedAreas", g.sharedAreas(), 1000, true);
        SourcedField<Integer> totalFloors = check.sourced("content.totalFloors", g.totalFloors(), SourceFamily.COUNT,
                false, false, (path, value) -> check.count(path, value, 1, 200));
        List<PartsSpace> spaces = entries(check, g.spaces(), PartsSpace::spaceId, (at, space) -> new PartsSpace(
                space.spaceId(),
                textField(check, at + ".floor", space.floor(), 50, false),
                textField(check, at + ".privateAreas", space.privateAreas(), 1000, false),
                check.field(at + ".actualAccess", space.actualAccess(), false,
                        (path, value) -> actualAccess(check, path, value))));
        return new PartsContent(accessRoute, sharedAreas, totalFloors, spaces);
    }

    private static ActualAccess actualAccess(BodyChecker check, String path, ActualAccess value) {
        AccessExtent extent = check.present(path + ".extent", value.extent());
        String note = extent == AccessExtent.ENTERED_PART
                ? check.text(path + ".note", value.note(), 500)
                : check.optionalText(path + ".note", value.note(), 500);
        return new ActualAccess(extent, note);
    }

    // ==================== G3 dimensions ====================

    private static DimensionsContent dimensions(BodyChecker check, DimensionsContent g) {
        return new DimensionsContent(entries(check, g.spaces(), DimensionsSpace::spaceId, (at, space) ->
                new DimensionsSpace(space.spaceId(),
                        check.sourced(at + ".area", space.area(), SourceFamily.AREA, false, false,
                                (path, value) -> area(check, path, value)),
                        count(check, at + ".bedrooms", space.bedrooms(), 0, 50),
                        count(check, at + ".privateBathrooms", space.privateBathrooms(), 0, 50),
                        check.sourced(at + ".sharedBathroomUse", space.sharedBathroomUse(), SourceFamily.COUNT, false,
                                false, (path, value) -> value),
                        check.optionalText(at + ".layoutNote", space.layoutNote(), 1000))));
    }

    /** Square metres from 0.1 to 99,999.9 with at most one decimal, kept at scale 1 so 45 and 45.0 are equal. */
    private static BigDecimal area(BodyChecker check, String path, BigDecimal value) {
        if (!BodyChecker.plausible(value) || value.compareTo(MIN_AREA) < 0 || value.compareTo(MAX_AREA) > 0
                || value.stripTrailingZeros().scale() > 1) {
            check.add(path, path + " must be between 0.1 and 99999.9 with at most one decimal.");
            return null;
        }
        return value.setScale(1, RoundingMode.UNNECESSARY);
    }

    // ==================== shared ====================

    private static Field<String> textField(BodyChecker check, String path, Field<String> field, int max,
                                           boolean naAllowed) {
        return check.field(path, field, naAllowed, (valuePath, value) -> check.text(valuePath, value, max));
    }

    private static SourcedField<Integer> count(BodyChecker check, String path, SourcedField<Integer> field, int min,
                                               int max) {
        return check.sourced(path, field, SourceFamily.COUNT, false, false,
                (valuePath, value) -> check.count(valuePath, value, min, max));
    }

    /**
     * The per-space entries of a group: a list (empty when nothing is recorded), each entry naming a space by a ULID
     * at most once (spec 4.6 "each id at most once"); whether G1 knows the space is a rule of save step 9.
     */
    static <T> List<T> entries(BodyChecker check, List<T> entries, Function<T, String> idOf,
                               BiFunction<String, T, T> rules) {
        String path = "content.spaces";
        if (check.list(path, entries, MAX_SPACES, "spaces") == null) {
            return null;
        }
        Set<String> ids = new HashSet<>();
        List<T> normalised = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String at = path + "." + i;
            uniqueId(check, at + ".spaceId", idOf.apply(entries.get(i)), ids);
            normalised.add(rules.apply(at, entries.get(i)));
        }
        return normalised;
    }
}
