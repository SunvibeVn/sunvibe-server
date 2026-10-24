package vn.sunvibe.survey.core.domain.model.checklist.unit;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What a group document keeps of a unit that left its content while its key can come back (survey-checklist spec
 * A-19, A-29, plan PD-1): the {@link UnitMeta} fields as the removal stamped them (the remover as last editor at the
 * removal time, the observer, date, last source and form field of the value it held, the correcting save) and, for a
 * hazard removed with an owner fix report, who heard that report and its date. Server-only: never returned, except a
 * scope tombstone as the metadata of a shared wet area read back in separate parts.
 */
public record Tombstone(String enteredByUserId, Instant enteredAt, String observedByUserId, LocalDate observedOn,
                        String formField, ChecklistSource lastSource, Long correctionSaveId,
                        OwnerReportFacts ownerReportOrNull) {

    /** The receiver and date of a removed hazard's owner fix report (survey-checklist BR-30); no report text. */
    public record OwnerReportFacts(String receivedByUserId, LocalDate reportedOn) {
    }

    /** The tombstone of a unit without report facts, holding its metadata as it is. */
    public static Tombstone of(UnitMeta meta) {
        return new Tombstone(meta.enteredByUserId(), meta.enteredAt(), meta.observedByUserId(), meta.observedOn(),
                meta.formField(), meta.lastSource(), meta.correctionSaveId(), null);
    }

    /** The metadata the unit takes again when its key comes back; the report facts stay in the tombstone. */
    public UnitMeta asMeta() {
        return new UnitMeta(enteredByUserId, enteredAt, observedByUserId, observedOn, formField, lastSource,
                correctionSaveId);
    }

    /** The same tombstone pointing to the save whose correction covered the removal. */
    public Tombstone withCorrection(Long saveId) {
        return new Tombstone(enteredByUserId, enteredAt, observedByUserId, observedOn, formField, lastSource, saveId,
                ownerReportOrNull);
    }
}
