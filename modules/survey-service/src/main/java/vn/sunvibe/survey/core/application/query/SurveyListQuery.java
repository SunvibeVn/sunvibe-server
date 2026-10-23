package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;

import java.util.Optional;

/**
 * One page of the survey list (survey-schedule US-4, spec 7.1, A-11): the scope, an optional surveyor (only with
 * {@code ALL}), a zero-based page and a size of 1..100.
 */
public record SurveyListQuery(SurveyListScope scope, Optional<String> surveyorUserId, int page, int size) {

    public static final int MAX_SIZE = 100;

    public SurveyListQuery {
        if (scope == null) {
            throw new InvalidSurveyValueException("scope", "scope must be mine or all.");
        }
        surveyorUserId = surveyorUserId == null ? Optional.empty() : surveyorUserId.filter(id -> !id.isBlank());
        if (scope == SurveyListScope.MINE && surveyorUserId.isPresent()) {
            throw new InvalidSurveyValueException("surveyorUserId", "surveyorUserId can only be given with scope all.");
        }
        if (page < 0) {
            throw new InvalidSurveyValueException("page", "page must be 0 or more.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidSurveyValueException("size", "size must be between 1 and " + MAX_SIZE + ".");
        }
    }
}
