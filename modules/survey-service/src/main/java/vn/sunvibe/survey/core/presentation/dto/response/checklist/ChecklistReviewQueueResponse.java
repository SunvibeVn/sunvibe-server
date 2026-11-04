package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** One page of the review queue (survey-checklist submission spec 7.1). */
@Schema(description = "One page of the review queue, oldest submission first")
public record ChecklistReviewQueueResponse(List<ChecklistReviewQueueItemResponse> items, int page, int size,
                                           long totalElements) {
}
