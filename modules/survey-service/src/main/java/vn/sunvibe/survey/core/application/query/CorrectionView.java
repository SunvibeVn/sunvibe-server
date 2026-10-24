package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectedUnit;

import java.time.Instant;
import java.util.List;

/** One correcting save (survey-checklist spec 7.2 {@code corrections[]}): private survey data, never logged. */
public record CorrectionView(String saveId, ChecklistGroup group, List<CorrectedUnit> units, String reason,
                             Basis basis, String actorUserId, Instant savedAt) {

    @Override
    public String toString() {
        return "CorrectionView[" + saveId + "]";
    }
}
