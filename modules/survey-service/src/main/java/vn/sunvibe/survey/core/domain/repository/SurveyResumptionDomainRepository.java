package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.resumption.SurveyResumption;

import java.util.List;

/** Domain repository of survey resumptions. Insert-only: a resumption is never updated or deleted. */
public interface SurveyResumptionDomainRepository {

    void save(SurveyResumption resumption);

    /** The resumptions of one lead, oldest first. */
    List<SurveyResumption> findByLeadIdOrderByOccurredAt(String leadId);
}
