package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/**
 * What survey-service needs to know about a lead the caller can see (survey-schedule spec 5.4): status and intent as
 * enum names, the current intent cycle, the assignee, the version a survey command must name, the latest activity
 * time, the request code and owner name of the survey list, the lead's place, when it was created and, for a held
 * lead, when it last went on hold (null otherwise). Contact name and place are private: never logged.
 */
public record SurveyLeadFacts(String leadId, String requestCode, String status, String intent, int intentCycle,
                              String assignedUserId, long version, Instant lastActivityAt, String contactName,
                              SurveyLeadPlace place, Instant createdAt, Instant onHoldSince) {

    /** Personal data: never print it. */
    @Override
    public String toString() {
        return "SurveyLeadFacts[" + leadId + "]";
    }
}
