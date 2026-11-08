package vn.sunvibe.survey.core.domain.model.log;

import java.time.Instant;

/**
 * One read of private survey data (survey-schedule BR-5, survey-schedule spec A-13; survey-checklist spec A-10): who, when, which lead
 * and what was shown: the survey block, a checklist or a checkpoint. Rows written before the checklist have no
 * resource (both null). Insert-only. The id is a Snowflake number given by the caller.
 */
public record SurveyAccessLog(long id, String leadId, String userId, Instant accessedAt,
                              AccessResourceType resourceTypeOrNull, String resourceIdOrNull,
                              String requestIdOrNull) {

    public SurveyAccessLog {
        if (leadId == null || userId == null || accessedAt == null) {
            throw new IllegalArgumentException("An access log entry needs its lead, user and time.");
        }
        if ((resourceTypeOrNull == null) != (resourceIdOrNull == null)) {
            throw new IllegalArgumentException("A resource has its type and id.");
        }
    }

    /** A row without the server request id (rows of the survey and checklist reads, survey-media spec A-19). */
    public SurveyAccessLog(long id, String leadId, String userId, Instant accessedAt,
                           AccessResourceType resourceTypeOrNull, String resourceIdOrNull) {
        this(id, leadId, userId, accessedAt, resourceTypeOrNull, resourceIdOrNull, null);
    }

    /** A read of the lead's survey block (survey-checklist spec A-10: resource SURVEY_BLOCK, id = the lead id). */
    public static SurveyAccessLog create(long id, String leadId, String userId, Instant accessedAt) {
        return new SurveyAccessLog(id, leadId, userId, accessedAt, AccessResourceType.SURVEY_BLOCK, leadId);
    }

    /** A read of a checklist or checkpoint, or a checklist command's answer. */
    public static SurveyAccessLog create(long id, String leadId, String userId, Instant accessedAt,
                                         AccessResourceType resourceType, String resourceId) {
        return new SurveyAccessLog(id, leadId, userId, accessedAt, resourceType, resourceId);
    }

    /** A delivered media read or list, with the server request id (survey-media spec A-6, A-19). */
    public static SurveyAccessLog create(long id, String leadId, String userId, Instant accessedAt,
                                         AccessResourceType resourceType, String resourceId, String requestId) {
        return new SurveyAccessLog(id, leadId, userId, accessedAt, resourceType, resourceId, requestId);
    }
}
