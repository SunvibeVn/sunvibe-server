package vn.sunvibe.survey.core.application.query.media;

import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;

/**
 * The answer to a content upload (survey-media spec 7.1, 202): the photo, the set version after it and the History
 * entry the commit wrote; a replay has none (spec 4.4 step 2).
 */
public record MediaContentResult(MediaItemView media, long setVersion, ChecklistHistoryEntry historyEntryOrNull) {
}
