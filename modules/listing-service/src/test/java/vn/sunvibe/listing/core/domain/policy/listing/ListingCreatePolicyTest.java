package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.policy.listing.ListingCreatePolicy.AnswerFacts;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.ENDED;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.NOT_STARTED;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.NO_AGREEMENT;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.OWNER;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.SUSPENDED;

/**
 * When a listing may be created (listing-publish BR-1, LP-1; E06-S1 spec E06S1-D3, section 5.1): the groups that stop
 * it, empty when it may be created.
 */
class ListingCreatePolicyTest {

    private static final AnswerFacts ALLOWED = new AnswerFacts(false, true, List.of());

    @Test
    void refusals_standingEnded_endedOnly() {
        assertEquals(List.of(ENDED), ListingCreatePolicy.refusals(true, ALLOWED));
        assertEquals(List.of(ENDED), ListingCreatePolicy.refusals(true, null));
        assertEquals(List.of(ENDED),
                ListingCreatePolicy.refusals(true, new AnswerFacts(false, false, List.of(SUSPENDED, OWNER))));
    }

    @Test
    void refusals_answerAbsent_noAgreement() {
        assertEquals(List.of(NO_AGREEMENT), ListingCreatePolicy.refusals(false, null));
    }

    @Test
    void refusals_answerEnded_ended() {
        assertEquals(List.of(ENDED), ListingCreatePolicy.refusals(false, new AnswerFacts(true, false, List.of(ENDED))));
        assertEquals(List.of(ENDED),
                ListingCreatePolicy.refusals(false, new AnswerFacts(true, false, List.of(NOT_STARTED))));
    }

    @Test
    void refusals_allowedAnswer_none() {
        assertEquals(List.of(), ListingCreatePolicy.refusals(false, ALLOWED));
    }

    @Test
    void refusals_notStartedAlone_none() {
        assertEquals(List.of(), ListingCreatePolicy.refusals(false, new AnswerFacts(false, false, List.of(NOT_STARTED))));
    }

    @Test
    void refusals_notStartedWithAnother_bothInE05Order() {
        assertEquals(List.of(NOT_STARTED, OWNER),
                ListingCreatePolicy.refusals(false, new AnswerFacts(false, false, List.of(NOT_STARTED, OWNER))));
    }

    @Test
    void refusals_suspendedAnswer_suspended() {
        List<AgreementReasonGroup> refusals =
                ListingCreatePolicy.refusals(false, new AnswerFacts(false, false, List.of(SUSPENDED)));

        assertEquals(List.of(SUSPENDED), refusals);
    }
}
