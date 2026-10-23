package vn.sunvibe.survey.core.application.usecase;

import vn.sunvibe.survey.core.application.query.OverlapView;
import vn.sunvibe.survey.core.application.query.SurveyBlock;
import vn.sunvibe.survey.core.application.query.SurveyListItem;
import vn.sunvibe.survey.core.application.query.SurveyListQuery;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.domain.repository.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Use Case - SurveyQueryUseCase
 * <p>
 * The survey reads of staff (survey-schedule US-4, spec 7.1): a lead's survey block (access-logged), the survey list
 * and the overlap pre-check (neither access-logged).
 */
public interface SurveyQueryUseCase {

    /** The survey block of a lead the viewer can see; writes one survey access-log row (survey-schedule spec A-13). */
    SurveyBlock block(SurveyViewer viewer, String leadId);

    /** One page of scheduled appointments; a row whose lead the viewer cannot see is left out. */
    PageResult<SurveyListItem> list(SurveyViewer viewer, SurveyListQuery query);

    /** The scheduled appointments of the lead's surveyor that overlap the planned time (survey-schedule spec A-10). */
    List<OverlapView> overlaps(SurveyViewer viewer, String leadId, Instant startAt, Integer durationMinutes,
                               Optional<String> excludeAppointmentId);
}
