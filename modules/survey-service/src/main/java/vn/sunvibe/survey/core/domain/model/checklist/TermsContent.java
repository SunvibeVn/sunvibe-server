package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/**
 * G8 (survey-checklist spec 4.6): surveyed or offered terms, common and per space; never the lead's price and never
 * agreed lease terms (D-AI-17).
 */
public record TermsContent(TermsCommon common, List<TermsSpace> spaces) implements GroupContent {

    @Override
    public ChecklistGroup group() {
        return ChecklistGroup.TERMS;
    }

    @Override
    public String toString() {
        return "TermsContent[" + (spaces == null ? 0 : spaces.size()) + " spaces]";
    }
}
