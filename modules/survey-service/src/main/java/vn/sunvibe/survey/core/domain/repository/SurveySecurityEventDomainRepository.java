package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;

import java.util.List;

/** Insert-only security events of the checklist boundary (survey-checklist spec 4.10, I-6). */
public interface SurveySecurityEventDomainRepository {

    void save(SurveySecurityEvent event);

    /** The events of a lead, oldest first. */
    List<SurveySecurityEvent> findByLeadId(String leadId);
}
