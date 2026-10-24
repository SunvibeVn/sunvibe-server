package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * The province and ward names of the observed address as the intake location rules returned them at the G1 save
 * (survey-checklist spec PD-1): server fields beside the content, outside its equality.
 */
public record ObservedPlaceNames(String provinceName, String wardName) {

    /** Part of a private address: never print it. */
    @Override
    public String toString() {
        return "ObservedPlaceNames[hidden]";
    }
}
