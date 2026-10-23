package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** What a proxy record or a resumption relies on: the Vietnam date of the confirmation and its text. */
@Schema(description = "The basis of a proxy record or a resumption")
public record SurveyBasisResponse(LocalDate confirmedOn, String text) {
}
