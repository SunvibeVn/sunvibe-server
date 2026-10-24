package vn.sunvibe.survey.core.domain.model.checklist.unit;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Server-kept metadata of a provenance unit (survey-checklist spec 4.5, 7.2): who last changed it and when, who
 * observed or heard the value it holds (the performer for on-site values, the receiver for owner statements, none for
 * the owner's form or a note) and on which day, the submission field of an owner's-form value, the source it last held
 * (kept when it is cleared, spec A-19) and the save whose correction covered the last change.
 */
public record UnitMeta(String enteredByUserId, Instant enteredAt, String observedByUserId, LocalDate observedOn,
                       String formField, ChecklistSource lastSource, Long correctionSaveId) {

    /** The same metadata pointing to the correcting save. */
    public UnitMeta withCorrection(Long saveId) {
        return new UnitMeta(enteredByUserId, enteredAt, observedByUserId, observedOn, formField, lastSource, saveId);
    }
}
