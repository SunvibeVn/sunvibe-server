package vn.sunvibe.survey.core.presentation.converter;

import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.command.BasisInput;
import vn.sunvibe.survey.core.application.command.CorrectionInput;
import vn.sunvibe.survey.core.application.command.CreateMediaCommand;
import vn.sunvibe.survey.core.application.command.DeselectMediaCommand;
import vn.sunvibe.survey.core.application.command.RemoveMediaCommand;
import vn.sunvibe.survey.core.presentation.dto.request.media.MediaCreateRequest;
import vn.sunvibe.survey.core.presentation.dto.request.media.MediaRemovalCorrectionRequest;
import vn.sunvibe.survey.core.presentation.dto.request.media.MediaRemovalRequest;

/** Converter - survey-media request bodies to commands (survey-media spec 7.2); values are checked by the use case. */
@Component
public class MediaRequestMapper {

    public RemoveMediaCommand toCommand(String mediaId, MediaRemovalRequest request) {
        return new RemoveMediaCommand(mediaId, correction(request));
    }

    public DeselectMediaCommand toDeselection(String checklistId, String mediaId, MediaRemovalRequest request) {
        return new DeselectMediaCommand(checklistId, mediaId, correction(request));
    }

    private static CorrectionInput correction(MediaRemovalRequest request) {
        MediaRemovalCorrectionRequest correction = request.correction();
        return correction == null ? null : new CorrectionInput(correction.reason(), correction.basis() == null ? null
                : new BasisInput(correction.basis().confirmedOn(), correction.basis().text()));
    }

    public CreateMediaCommand toCommand(String checklistId, MediaCreateRequest request) {
        return new CreateMediaCommand(checklistId, request.uploadKey(), request.spaceId(), request.category(),
                request.caption(), request.sizeBytes(), request.contentSha256());
    }
}
