package vn.sunvibe.survey.core.domain.model.log;

import java.time.Instant;

/**
 * One read of a lead's survey block, which holds places, access notes and on-site contacts (survey-schedule BR-5,
 * spec A-13): who, when, which lead. Insert-only. The id is a Snowflake number given by the caller.
 */
public record SurveyAccessLog(long id, String leadId, String userId, Instant accessedAt) {

    public SurveyAccessLog {
        if (leadId == null || userId == null || accessedAt == null) {
            throw new IllegalArgumentException("An access log entry needs its lead, user and time.");
        }
    }

    public static SurveyAccessLog create(long id, String leadId, String userId, Instant accessedAt) {
        return new SurveyAccessLog(id, leadId, userId, accessedAt);
    }
}
