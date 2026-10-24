package vn.sunvibe.survey.core.domain.policy.checklist;

import com.github.f4b6a3.ulid.Ulid;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;
import vn.sunvibe.survey.core.domain.model.checklist.SourceFamily;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Collects every broken rule of one checklist body while it normalises the values (survey-checklist spec 5.5 save
 * step 1, PD-2): text through {@link SurveyText} (NFC, trimmed, optional blank is absent, phone rule, spec A-11), the
 * wrapper consistency of spec 4.6 and the source rules. Paths are JSON paths of the request; messages never quote a
 * value. A rule that fails returns null, so the caller keeps walking the body and reports all violations at once.
 */
final class BodyChecker {

    /** Length of the reasons, relay bases and basis texts of spec 4.6 and 5.2. */
    static final int REASON_MAX = 500;

    private final LocalDate today;
    private final List<Violation> violations = new ArrayList<>();

    BodyChecker(LocalDate today) {
        this.today = today;
    }

    LocalDate today() {
        return today;
    }

    void add(String path, String message) {
        violations.add(new Violation(path, message));
    }

    void required(String path) {
        add(path, path + " is required.");
    }

    void mustBeEmpty(String path, String when) {
        add(path, path + " must be empty " + when + ".");
    }

    boolean hasViolations() {
        return !violations.isEmpty();
    }

    void throwIfAny() {
        if (!violations.isEmpty()) {
            throw new ChecklistValidationException(violations);
        }
    }

    // ==================== text ====================

    /** Required text of 1..max characters without a phone number, cleaned; null after a violation. */
    String text(String path, String value, int max) {
        try {
            return SurveyText.require(path, value, max);
        } catch (InvalidSurveyValueException e) {
            add(path, e.getMessage());
            return null;
        }
    }

    /** Optional text: null when absent or blank, else as {@link #text}. */
    String optionalText(String path, String value, int max) {
        try {
            return SurveyText.optional(path, value, max);
        } catch (InvalidSurveyValueException e) {
            add(path, e.getMessage());
            return null;
        }
    }

    // ==================== values ====================

    /** A client id of a space, hazard or item: a ULID (spec A-23). */
    String ulid(String path, String value) {
        if (value == null) {
            required(path);
            return null;
        }
        if (!Ulid.isValid(value)) {
            add(path, path + " must be a ULID.");
            return null;
        }
        return value;
    }

    Integer count(String path, Integer value, int min, int max) {
        if (value == null) {
            required(path);
            return null;
        }
        if (value < min || value > max) {
            add(path, path + " must be between " + min + " and " + max + ".");
            return null;
        }
        return value;
    }

    <E> E present(String path, E value) {
        if (value == null) {
            required(path);
        }
        return value;
    }

    /** A decimal small enough to compare and scale safely (no 1E+999999999 reaches arithmetic). */
    static boolean plausible(BigDecimal value) {
        return value.precision() <= 20 && Math.abs(value.scale()) <= 20;
    }

    <T> List<T> list(String path, List<T> value, int max, String what) {
        if (value == null) {
            required(path);
            return null;
        }
        if (value.size() > max) {
            add(path, path + " must hold at most " + max + " " + what + ".");
            return null;
        }
        for (int i = 0; i < value.size(); i++) {
            if (value.get(i) == null) {
                required(path + "." + i);
                return null;
            }
        }
        return value;
    }

    // ==================== wrappers ====================

    /**
     * An unsourced field (spec 4.6): {@code value} exactly when VALUE, {@code naReason} 1..500 exactly when
     * NOT_APPLICABLE and only where the field allows it. The value rule gets the value's path and returns the
     * normalised value or null after a violation.
     */
    <V> Field<V> field(String path, Field<V> field, boolean naAllowed, BiFunction<String, V, V> valueRule) {
        if (field == null) {
            required(path);
            return null;
        }
        FieldState state = field.state();
        if (state == null) {
            required(path + ".state");
            return null;
        }
        return switch (state) {
            case VALUE -> {
                V value = value(path, field.value(), valueRule);
                noReason(path, field.naReason());
                yield new Field<>(state, value, null);
            }
            case UNKNOWN -> {
                noValue(path, field.value());
                noReason(path, field.naReason());
                yield Field.unknown();
            }
            case NOT_APPLICABLE -> {
                if (!naAllowed) {
                    add(path + ".state", path + ".state cannot be NOT_APPLICABLE for this field.");
                    yield null;
                }
                noValue(path, field.value());
                yield Field.notApplicable(text(path + ".naReason", field.naReason(), REASON_MAX));
            }
        };
    }

    /**
     * A sourced field (spec 4.6, A-18): as {@link #field}, plus a source of the field's family with VALUE and only
     * then; owner's form only where the field allows it; {@code confirmedOn} (2000-01-01..today) and
     * {@code receivedByUserId} exactly for an owner statement; {@code relayBasis} only on an owner statement (whether
     * the caller needs one is a rule of save step 9).
     */
    <V> SourcedField<V> sourced(String path, SourcedField<V> field, SourceFamily family, boolean ownerFormAllowed,
                                boolean naAllowed, BiFunction<String, V, V> valueRule) {
        if (field == null) {
            required(path);
            return null;
        }
        FieldState state = field.state();
        if (state == null) {
            required(path + ".state");
            return null;
        }
        if (state != FieldState.VALUE) {
            if (field.source() != null) {
                mustBeEmpty(path + ".source", "unless state is VALUE");
            }
            noStatement(path, field.confirmedOn(), field.receivedByUserId(), field.relayBasis(),
                    "unless state is VALUE");
            Field<V> plain = field(path, new Field<>(state, field.value(), field.naReason()), naAllowed, valueRule);
            return plain == null ? null : new SourcedField<>(plain.state(), null, plain.naReason(), null, null, null,
                    null);
        }
        V value = value(path, field.value(), valueRule);
        noReason(path, field.naReason());
        ChecklistSource source = source(path, field.source(), family, ownerFormAllowed);
        if (source != ChecklistSource.OWNER_STATED) {
            noStatement(path, field.confirmedOn(), field.receivedByUserId(), field.relayBasis(),
                    "unless the owner said it");
            return new SourcedField<>(state, value, null, source, null, null, null);
        }
        Statement statement = statement(path, field.confirmedOn(), field.receivedByUserId(), field.relayBasis());
        return new SourcedField<>(state, value, null, source, statement.confirmedOn(), statement.receivedByUserId(),
                statement.relayBasis());
    }

    /** The source of a sourced value: present, of the family, owner's form only where allowed. */
    ChecklistSource source(String path, ChecklistSource source, SourceFamily family, boolean ownerFormAllowed) {
        if (source == null) {
            required(path + ".source");
            return null;
        }
        if (!family.allows(source)) {
            add(path + ".source", path + ".source is not a source of this field.");
            return null;
        }
        if (source == ChecklistSource.OWNER_FORM && !ownerFormAllowed) {
            add(path + ".source", path + ".source cannot be OWNER_FORM for this field.");
            return null;
        }
        return source;
    }

    /** The parts of an owner statement or owner fix report: date, receiver and optional relay basis (spec A-18). */
    Statement statement(String path, LocalDate confirmedOn, String receivedByUserId, String relayBasis) {
        return statement(path, "confirmedOn", confirmedOn, receivedByUserId, relayBasis, true);
    }

    /**
     * As {@link #statement(String, String, LocalDate, String, String, boolean)} with the date under another name;
     * {@code checkDateRange} false leaves the range to a rule that needs stored data (owner fix reports, spec A-7).
     */
    Statement statement(String path, String dateName, LocalDate date, String receivedByUserId, String relayBasis,
                        boolean checkDateRange) {
        String datePath = path + "." + dateName;
        if (date == null) {
            required(datePath);
        } else if (checkDateRange) {
            ChecklistDateRules.requireStatementDate(datePath, date, today).ifPresent(violations::add);
        }
        String receiverPath = path + ".receivedByUserId";
        String receiver = null;
        if (receivedByUserId == null) {
            required(receiverPath);
        } else if (!Ulid.isValid(receivedByUserId)) {
            add(receiverPath, receiverPath + " must be a user id.");
        } else {
            receiver = receivedByUserId;
        }
        return new Statement(date, receiver, optionalText(path + ".relayBasis", relayBasis, REASON_MAX));
    }

    /** No date, receiver or relay basis outside an owner statement. */
    void noStatement(String path, LocalDate confirmedOn, String receivedByUserId, String relayBasis, String when) {
        if (confirmedOn != null) {
            mustBeEmpty(path + ".confirmedOn", when);
        }
        if (receivedByUserId != null) {
            mustBeEmpty(path + ".receivedByUserId", when);
        }
        if (relayBasis != null) {
            mustBeEmpty(path + ".relayBasis", when);
        }
    }

    private <V> V value(String path, V value, BiFunction<String, V, V> valueRule) {
        if (value == null) {
            add(path + ".value", path + ".value is required when state is VALUE.");
            return null;
        }
        return valueRule.apply(path + ".value", value);
    }

    private void noValue(String path, Object value) {
        if (value != null) {
            mustBeEmpty(path + ".value", "unless state is VALUE");
        }
    }

    private void noReason(String path, String naReason) {
        if (naReason != null) {
            mustBeEmpty(path + ".naReason", "unless state is NOT_APPLICABLE");
        }
    }

    /** The normalised parts of an owner statement. */
    record Statement(LocalDate confirmedOn, String receivedByUserId, String relayBasis) {
    }
}
