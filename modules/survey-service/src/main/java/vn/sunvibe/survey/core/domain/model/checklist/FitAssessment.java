package vn.sunvibe.survey.core.domain.model.checklist;

/** The fit judgement with its reason (survey-checklist spec 4.6: reason 1..1000). Reason text: never printed. */
public record FitAssessment(Fit assessment, String reason) {

    @Override
    public String toString() {
        return "FitAssessment[" + assessment + "]";
    }
}
