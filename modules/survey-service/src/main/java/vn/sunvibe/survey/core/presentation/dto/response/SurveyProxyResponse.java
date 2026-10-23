package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why a visit was recorded by someone other than the performer, and on what basis. */
@Schema(description = "Reason and basis of a proxy visit record")
public record SurveyProxyResponse(String reason, SurveyBasisResponse basis) {
}
