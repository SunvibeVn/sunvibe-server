package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * The source of a sourced checklist value (survey-checklist spec 4.6). Which sources a field accepts is its
 * {@link SourceFamily}; the template rules refuse a source of another family.
 */
public enum ChecklistSource {
    /** Seen on site at the draft's visit. */
    OBSERVED_ON_SITE(SourceKind.ON_SITE),
    /** Measured on site at the draft's visit (areas). */
    MEASURED(SourceKind.ON_SITE),
    /** Estimated on site at the draft's visit (areas). */
    ESTIMATED(SourceKind.ON_SITE),
    /** Said by the owner, with the date and the staff member who heard it. */
    OWNER_STATED(SourceKind.OWNER_STATED),
    /** Held by the owner's own web submission (three fields only, spec A-18). */
    OWNER_FORM(SourceKind.OWNER_FORM);

    private final SourceKind kind;

    ChecklistSource(SourceKind kind) {
        this.kind = kind;
    }

    public SourceKind kind() {
        return kind;
    }
}
