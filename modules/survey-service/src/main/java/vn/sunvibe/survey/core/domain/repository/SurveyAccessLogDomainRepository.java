package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;

import java.util.List;

/** Domain repository of the survey access log. Insert-only: an entry is never updated or deleted. */
public interface SurveyAccessLogDomainRepository {

    void save(SurveyAccessLog log);

    /** The reads of one lead's survey block, oldest first. */
    List<SurveyAccessLog> findByLeadId(String leadId);
}
