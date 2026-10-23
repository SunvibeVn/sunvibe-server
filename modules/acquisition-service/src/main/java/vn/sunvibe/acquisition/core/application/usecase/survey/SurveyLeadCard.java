package vn.sunvibe.acquisition.core.application.usecase.survey;

/**
 * The lead fields one row of the survey list shows (survey-schedule US-4, spec 7.2): no phone, address or pin. The
 * owner's name is personal data: never logged.
 */
public record SurveyLeadCard(String leadId, String requestCode, String contactName, String wardName) {

    /** Personal data: never print it. */
    @Override
    public String toString() {
        return "SurveyLeadCard[" + leadId + "]";
    }
}
