package vn.sunvibe.survey.core.application.shared.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.github.f4b6a3.ulid.Ulid;

import vn.sunvibe.survey.core.application.command.media.CreateMediaCommand;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.domain.exception.common.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.common.SurveyText;
import vn.sunvibe.survey.core.domain.policy.media.MediaFileRules;

/**
 * The body rules of survey-media commands, checked before any lookup (spec 5.4 create (1), A-5, A-11; W16 A-4): every
 * rule a body breaks on its JSON path, so one 400 lists them all. Messages name the field, never the value. Client ids
 * are ULIDs by the same check as the checklist's space ids, so every G1 space id is accepted.
 */
public final class MediaInputs {

    /** spec A-11: a caption of 1..200 characters. */
    public static final int CAPTION_MAX = 200;

    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private MediaInputs() {
    }

    /** A create body after its checks: the caption cleaned, the hash in lower case. */
    public record CreateInput(String checklistId, String uploadKey, String spaceIdOrNull, EvidenceCategory category,
                              String captionOrNull, long sizeBytes, String sha256) {

        /** spec 4.4 step 1: the stored photo of this key was created with this same body. */
        public boolean sameBodyAs(SurveyMedia media) {
            return Objects.equals(spaceIdOrNull, media.getPlacement().spaceIdOrNull())
                    && category == media.getCategory() && Objects.equals(captionOrNull, media.getCaption())
                    && sizeBytes == media.getDeclaredSizeBytes() && sha256.equals(media.getSourceSha256());
        }

        /** The caption and the hash are not logged. */
        @Override
        public String toString() {
            return "CreateInput[" + checklistId + "]";
        }
    }

    /**
     * spec 5.4 create (1): ULIDs, the category, the caption (1..200 without a phone number), {@code sizeBytes}
     * 1..20,971,520 and {@code contentSha256} of 64 hexadecimal characters.
     *
     * @throws vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException with every violation
     */
    public static CreateInput create(CreateMediaCommand command) {
        List<Violation> violations = new ArrayList<>();
        if (command.uploadKey() == null) {
            violations.add(new Violation("uploadKey", "uploadKey is required."));
        } else if (!Ulid.isValid(command.uploadKey())) {
            violations.add(new Violation("uploadKey", "uploadKey must be a ULID."));
        }
        if (command.spaceIdOrNull() != null && !Ulid.isValid(command.spaceIdOrNull())) {
            violations.add(new Violation("spaceId", "spaceId must be a ULID."));
        }
        if (command.category() == null) {
            violations.add(new Violation("category", "category is required."));
        }
        String caption = null;
        try {
            caption = SurveyText.optional("caption", command.captionOrNull(), CAPTION_MAX);
        } catch (InvalidSurveyValueException e) {
            violations.add(new Violation("caption", e.getMessage()));
        }
        if (command.sizeBytes() == null) {
            violations.add(new Violation("sizeBytes", "sizeBytes is required."));
        } else if (!MediaFileRules.sizeAccepted(command.sizeBytes())) {
            violations.add(new Violation("sizeBytes", "sizeBytes must be between 1 and "
                    + MediaFileRules.MAX_FILE_BYTES + "."));
        }
        if (command.contentSha256() == null) {
            violations.add(new Violation("contentSha256", "contentSha256 is required."));
        } else if (!SHA256.matcher(command.contentSha256()).matches()) {
            violations.add(new Violation("contentSha256", "contentSha256 must be 64 hexadecimal characters."));
        }
        ChecklistInputs.throwIfAny(violations);
        return new CreateInput(command.checklistId(), command.uploadKey(), command.spaceIdOrNull(),
                command.category(), caption, command.sizeBytes(), command.contentSha256().toLowerCase(Locale.ROOT));
    }
}
