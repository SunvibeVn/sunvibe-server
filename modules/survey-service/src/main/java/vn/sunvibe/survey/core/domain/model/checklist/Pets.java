package vn.sunvibe.survey.core.domain.model.checklist;

/** The pets policy, with a note required when conditional (survey-checklist BR-5). Note: never printed. */
public record Pets(PetsPolicy policy, String note) {

    @Override
    public String toString() {
        return "Pets[" + policy + "]";
    }
}
