package vn.sunvibe.survey.core.presentation.converter;

import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.query.MediaContentResult;
import vn.sunvibe.survey.core.application.query.MediaStateResult;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaStateResponse;
import vn.sunvibe.survey.core.application.query.MediaCreateResult;
import vn.sunvibe.survey.core.application.query.MediaItemView;
import vn.sunvibe.survey.core.application.query.MediaListView;
import vn.sunvibe.survey.core.domain.policy.media.KnownVersion;
import vn.sunvibe.survey.core.presentation.dto.response.media.ChecklistMediaResponse;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaContentResponse;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaCorrection;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaCreateResponse;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaHold;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaItem;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaRemoval;

/** Converter - survey-media views to response DTOs (survey-media spec 7.2). */
@Component
public class MediaResponseConverter {

    public SurveyMediaCreateResponse toCreated(MediaCreateResult result) {
        return new SurveyMediaCreateResponse(toItem(result.media()), result.setVersion());
    }

    public SurveyMediaStateResponse toState(MediaStateResult result) {
        return new SurveyMediaStateResponse(toItem(result.media()), result.setVersion());
    }

    public SurveyMediaContentResponse toContent(MediaContentResult result) {
        return new SurveyMediaContentResponse(toItem(result.media()), result.setVersion(),
                result.historyEntryOrNull() == null ? null
                        : ChecklistResponseConverter.toEntry(result.historyEntryOrNull()));
    }

    public ChecklistMediaResponse toList(MediaListView view) {
        KnownVersion basis = view.basis();
        return new ChecklistMediaResponse(view.checklistId(), basis.setVersion(),
                new ChecklistMediaResponse.Basis(basis.identificationVersion(), basis.conditionVersion(),
                        basis.checklistVersion(), basis.stateEpoch(), basis.viewDigest()),
                view.uploadEnabled(), view.intakePaused(), view.servingEnabled(), view.canHold(),
                new ChecklistMediaResponse.Limits(view.limits().maxFileBytes(), view.limits().maxSelected(),
                        view.limits().selectedCount(), view.limits().maxPending(), view.limits().pendingCount()),
                view.items().stream().map(this::toItem).toList(),
                view.notAcceptedItems().stream().map(this::toItem).toList(),
                view.removedItems().stream().map(this::toItem).toList(),
                new ChecklistMediaResponse.Totals(view.totalNotAccepted(), view.totalRemoved()),
                view.coverage().stream().map(pair -> new ChecklistMediaResponse.Coverage(pair.spaceIdOrNull(),
                        pair.category(), pair.required(), pair.notRequiredReasonOrNull(), pair.readyCount(),
                        pair.processingCount(), pair.state())).toList());
    }

    public SurveyMediaItem toItem(MediaItemView view) {
        MediaItemView.Removed removed = view.removedOrNull();
        MediaItemView.Held held = view.heldOrNull();
        return new SurveyMediaItem(view.id(), view.mediaNo(), view.status(), view.statusReasonOrNull(),
                view.spaceIdOrNull(), view.spaceCodeAtUploadOrNull(), view.category(), view.captionOrNull(),
                view.captionHidden(), view.uploadedByUserId(), view.uploadedByNameOrNull(), view.createdAt(),
                view.contentReceivedAtOrNull(), view.readyAtOrNull(), view.widthOrNull(), view.heightOrNull(),
                removed == null ? null : new SurveyMediaRemoval(removed.at(), removed.byUserId(),
                        removed.byNameOrNull(), removed.kind(), correction(removed.correctionOrNull()),
                        removed.bySaveIdOrNull()),
                held == null ? null : new SurveyMediaHold(held.at(), held.byUserIdOrNull(), held.byNameOrNull(),
                        held.reasonOrNull()),
                view.contentAvailable(), view.unavailableReasonOrNull(), view.carriedFromChecklistNoOrNull());
    }

    private static SurveyMediaCorrection correction(MediaRemovalCorrection correction) {
        return correction == null ? null : new SurveyMediaCorrection(correction.reason(),
                new SurveyMediaCorrection.Basis(correction.basis().confirmedOn(), correction.basis().text()));
    }
}
