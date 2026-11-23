package vn.sunvibe.listing.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.command.FreshnessRecordCommand;
import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.application.command.PutLiveCommand;
import vn.sunvibe.listing.core.application.command.RevisionCopyCommand;
import vn.sunvibe.listing.core.application.command.RevisionDecisionCommand;
import vn.sunvibe.listing.core.application.command.RevisionFeaturesInput;
import vn.sunvibe.listing.core.application.command.RevisionSaveCommand;
import vn.sunvibe.listing.core.application.command.RevisionStartCommand;
import vn.sunvibe.listing.core.application.command.RevisionSubmitCommand;
import vn.sunvibe.listing.core.application.command.RevisionWithdrawCommand;
import vn.sunvibe.listing.core.application.command.TermsCaptureCommand;
import vn.sunvibe.listing.core.application.command.UnitLocationOpenCommand;
import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.RejectionGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.presentation.dto.request.ListingCreateRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingFeaturesInput;
import vn.sunvibe.listing.core.presentation.dto.request.ListingFreshnessRecordRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionCommandRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionContentRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionDecisionRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingTermsCaptureRequest;

import java.util.List;
import java.util.regex.Pattern;

/** Request DTO to command: a pure function of its arguments. */
@Component
public class ListingRequestConverter {

    /** listing-publish E06-S2 spec E06S2-D1: 1 to 9 decimal digits without a leading zero. */
    private static final Pattern REVISION_NO = Pattern.compile("^[1-9][0-9]{0,8}$");

    public ListingCreateCommand toCommand(ListingCreateRequest request) {
        return new ListingCreateCommand(request.agreementId());
    }

    /**
     * The revision number of a path (listing-publish E06-S2 plan E06S2-P3): anything else, a leading zero, a sign, a
     * blank, a digit of another script or a number above nine digits, is none, which the use case answers as a revision
     * that does not exist.
     */
    public static Integer revisionNoOrNull(String text) {
        return text != null && REVISION_NO.matcher(text).matches() ? Integer.valueOf(text) : null;
    }

    public RevisionStartCommand toStartCommand(String listingId, ListingRevisionCommandRequest request) {
        return new RevisionStartCommand(listingId, request.dataVersion());
    }

    public RevisionSubmitCommand toSubmitCommand(String listingId, Integer revisionNoOrNull,
                                                 ListingRevisionCommandRequest request) {
        return new RevisionSubmitCommand(listingId, revisionNoOrNull, request.dataVersion());
    }

    public RevisionWithdrawCommand toWithdrawCommand(String listingId, Integer revisionNoOrNull,
                                                     ListingRevisionCommandRequest request) {
        return new RevisionWithdrawCommand(listingId, revisionNoOrNull, request.dataVersion());
    }

    /** The texts in the form the request constraints checked and the revision stores (E06S2-D4). */
    public RevisionSaveCommand toCommand(String listingId, Integer revisionNoOrNull,
                                         ListingRevisionContentRequest request) {
        ListingFeaturesInput features = request.features();
        return new RevisionSaveCommand(listingId, revisionNoOrNull, request.dataVersion(),
                RevisionContent.normalized(request.title()), RevisionContent.normalized(request.description()),
                new RevisionFeaturesInput(features.furnishing(), features.airConditioner(), features.waterHeater(),
                        features.fridge(), features.washingMachine(), features.kitchen(), features.balcony(),
                        features.elevator(), features.motorbikeParking(), features.carParking(), features.internet(),
                        features.pets(), features.noSharedLandlord()));
    }

    public TermsCaptureCommand toCommand(String listingId, Integer revisionNoOrNull,
                                         ListingTermsCaptureRequest request) {
        return new TermsCaptureCommand(listingId, revisionNoOrNull, request.dataVersion(), request.source());
    }

    /**
     * The codes the request constraints checked as domain values (E06S4-D6); an absent list is none; the note as typed,
     * which the decision normalizes.
     */
    public RevisionDecisionCommand toDecisionCommand(String listingId, Integer revisionNoOrNull,
                                                     ListingRevisionDecisionRequest request) {
        return new RevisionDecisionCommand(listingId, revisionNoOrNull, request.dataVersion(),
                ReviewOutcome.valueOf(request.outcome()),
                request.checks() == null ? List.of() : request.checks().stream().map(ReviewCheck::valueOf).toList(),
                request.changeReasons() == null ? List.of()
                        : request.changeReasons().stream().map(ChangeReason::valueOf).toList(),
                request.rejectionGroup() == null ? null : RejectionGroup.valueOf(request.rejectionGroup()),
                request.note());
    }

    public UnitLocationOpenCommand toLocationCommand(String listingId, Integer revisionNoOrNull) {
        return new UnitLocationOpenCommand(listingId, revisionNoOrNull);
    }

    public RevisionCopyCommand toCopyCommand(String listingId, Integer revisionNoOrNull,
                                             ListingRevisionCommandRequest request) {
        return new RevisionCopyCommand(listingId, revisionNoOrNull, request.dataVersion());
    }

    public PutLiveCommand toPutLiveCommand(String listingId, Integer revisionNoOrNull,
                                           ListingRevisionCommandRequest request) {
        return new PutLiveCommand(listingId, revisionNoOrNull, request.dataVersion());
    }

    /** The codes the request constraints checked as domain values; the note as typed, which the record normalizes. */
    public FreshnessRecordCommand toCommand(String listingId, ListingFreshnessRecordRequest request) {
        return new FreshnessRecordCommand(listingId, request.dataVersion(), FreshnessChannel.valueOf(request.channel()),
                FreshnessOutcome.valueOf(request.outcome()), request.termsStillApply(), request.unitUnchanged(),
                request.note());
    }
}
