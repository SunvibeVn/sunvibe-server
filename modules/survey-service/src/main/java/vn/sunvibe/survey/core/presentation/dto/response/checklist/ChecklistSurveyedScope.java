package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;

import java.util.List;

/** What a PASSED revision surveyed: the G1 rental scope and spaces, never the brokerage scope (spec 4.7). */
@Schema(description = "The rental scope and spaces of the PASSED revision")
public record ChecklistSurveyedScope(RentalScope rentalScope, List<ChecklistSurveyedSpace> spaces) {
}
