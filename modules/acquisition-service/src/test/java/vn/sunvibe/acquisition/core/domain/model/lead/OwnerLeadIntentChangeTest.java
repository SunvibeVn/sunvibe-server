package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.exception.IntentChangeNotAllowedException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent.*;
import static vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus.*;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.LONG_AGO;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.lead;

/**
 * The requirement change of survey-schedule US-5, BR-10 (spec 4.6, A-17, A-25): rental intents only, the price in the
 * new intent's unit or none, a new intent cycle, and the lead moves ASSIGNED/SURVEY_SCHEDULED → CONTACTED.
 */
class OwnerLeadIntentChangeTest {

    private static final String ASSIGNEE = "01JUSERAAAAAAAAAAAAAAAAAAA";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    /** The fixture leads were created on 2026-10-01 (Vietnam date). */
    private static final LocalDate CREATED_ON = LocalDate.parse("2026-10-01");
    private static final List<OwnerLeadStatus> ALLOWED = List.of(ASSIGNED, CONTACTED, ON_HOLD, SURVEY_SCHEDULED);

    private static AskingPrice night(long amount) {
        return AskingPrice.of(amount, "VND", PriceUnit.NIGHT, SHORT_TERM_RENT);
    }

    private static AskingPrice month(long amount) {
        return AskingPrice.of(amount, "VND", PriceUnit.MONTH, LONG_TERM_RENT);
    }

    private static ConfirmationBasis basis() {
        return new ConfirmationBasis(CREATED_ON, "Owner confirmed by phone");
    }

    @Test
    void changeIntent_fromAssigned_movesToContactedAndStartsCycleTwo() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        IntentChange change = lead.changeIntent(SHORT_TERM_RENT, Optional.of(night(900_000)), basis());

        assertEquals(CONTACTED, lead.getStatus());
        assertEquals(SHORT_TERM_RENT, lead.getIntent());
        assertEquals(Optional.of(night(900_000)), lead.getAskingPrice());
        assertEquals(2, lead.getIntentCycle());
        assertEquals(LONG_TERM_RENT, change.fromIntent());
        assertEquals(Optional.empty(), change.fromPrice());
        assertEquals(SHORT_TERM_RENT, change.toIntent());
        assertEquals(Optional.of(night(900_000)), change.toPrice());
        assertEquals(2, change.newIntentCycle());
        assertEquals(OwnerLeadActivityType.INTENT_CHANGED, change.change().type());
        assertEquals(ASSIGNED, change.change().fromStatus());
        assertEquals(CONTACTED, change.change().toStatus());
        assertNull(change.change().note());
        assertNull(change.change().referenceId());
        assertTrue(lead.getUpdatedAt().isAfter(LONG_AGO));
    }

    @Test
    void changeIntent_fromSurveyScheduled_movesToContacted() {
        OwnerLead lead = lead(SURVEY_SCHEDULED, ASSIGNEE);

        IntentChange change = lead.changeIntent(SHORT_TERM_RENT, Optional.empty(), basis());

        assertEquals(CONTACTED, lead.getStatus());
        assertEquals(SURVEY_SCHEDULED, change.change().fromStatus());
        assertEquals(CONTACTED, change.change().toStatus());
    }

    @Test
    void changeIntent_fromContactedOrOnHold_keepsTheStatus() {
        for (OwnerLeadStatus from : List.of(CONTACTED, ON_HOLD)) {
            OwnerLead lead = lead(from, ASSIGNEE);

            IntentChange change = lead.changeIntent(SHORT_TERM_RENT, Optional.empty(), basis());

            assertEquals(from, lead.getStatus(), from.name());
            assertNull(change.change().fromStatus(), from.name());
            assertNull(change.change().toStatus(), from.name());
        }
    }

    @Test
    void changeIntent_twice_countsTheCycles() {
        OwnerLead lead = lead(CONTACTED, ASSIGNEE);

        lead.changeIntent(SHORT_TERM_RENT, Optional.empty(), basis());
        IntentChange second = lead.changeIntent(LONG_TERM_RENT, Optional.of(month(12_000_000)), basis());

        assertEquals(3, lead.getIntentCycle());
        assertEquals(3, second.newIntentCycle());
        assertEquals(SHORT_TERM_RENT, second.fromIntent());
        assertEquals(LONG_TERM_RENT, lead.getIntent());
    }

    @Test
    void changeIntent_saleLeadToLongTermRent_takesAMonthPrice() {
        AskingPrice total = AskingPrice.of(3_000_000_000L, "VND", PriceUnit.TOTAL, SALE);
        OwnerLead lead = lead(CONTACTED, ASSIGNEE, SALE, Optional.of(total));

        IntentChange change = lead.changeIntent(LONG_TERM_RENT, Optional.of(month(15_000_000)), basis());

        assertEquals(SALE, change.fromIntent());
        assertEquals(Optional.of(total), change.fromPrice());
        assertEquals(Optional.of(month(15_000_000)), lead.getAskingPrice());
    }

    @Test
    void changeIntent_withoutPrice_clearsThePrice() {
        OwnerLead lead = lead(CONTACTED, ASSIGNEE, LONG_TERM_RENT, Optional.of(month(12_000_000)));

        IntentChange change = lead.changeIntent(SHORT_TERM_RENT, Optional.empty(), basis());

        assertEquals(Optional.empty(), lead.getAskingPrice());
        assertEquals(Optional.of(month(12_000_000)), change.fromPrice());
        assertEquals(Optional.empty(), change.toPrice());
    }

    @Test
    void changeIntent_neverTouchesTheSubmissionSnapshot() {
        OwnerLead lead = lead(CONTACTED, ASSIGNEE);

        lead.changeIntent(SHORT_TERM_RENT, Optional.empty(), basis());

        assertEquals("{}", lead.getSubmissionSnapshot());
    }

    @Test
    void changeIntent_toSaleOrTheSameIntent_isRefusedOnNewIntent() {
        OwnerLead lead = lead(CONTACTED, ASSIGNEE);

        assertField("newIntent", () -> lead.changeIntent(SALE, Optional.empty(), basis()));
        assertField("newIntent", () -> lead.changeIntent(LONG_TERM_RENT, Optional.empty(), basis()));
        assertEquals(1, lead.getIntentCycle());
        assertEquals(LONG_TERM_RENT, lead.getIntent());
    }

    @Test
    void changeIntent_priceInAnotherUnit_isRefusedOnTheUnit() {
        OwnerLead lead = lead(CONTACTED, ASSIGNEE);

        assertField("askingPrice.unit", () -> lead.changeIntent(SHORT_TERM_RENT, Optional.of(month(5)), basis()));
        assertEquals(LONG_TERM_RENT, lead.getIntent());
    }

    @Test
    void changeIntent_notAllowedStatus_isRefusedWithTheFourStatuses() {
        for (OwnerLeadStatus from : OwnerLeadStatus.values()) {
            if (ALLOWED.contains(from)) {
                continue;
            }
            OwnerLead lead = lead(from, ASSIGNEE);
            // The state is checked before the values that need the lead (same intent, basis date)
            IntentChangeNotAllowedException ex = assertThrows(IntentChangeNotAllowedException.class,
                    () -> lead.changeIntent(LONG_TERM_RENT, Optional.empty(),
                            new ConfirmationBasis(CREATED_ON.minusDays(5), "text")), from.name());

            assertEquals(from, ex.getStatus(), from.name());
            assertEquals(ALLOWED, ex.getAllowed(), from.name());
            assertEquals(from, lead.getStatus(), from.name());
            assertEquals(1, lead.getIntentCycle(), from.name());
        }
    }

    @Test
    void changeIntent_basisBeforeTheLeadWasCreated_isRefusedOnConfirmedOn() {
        OwnerLead lead = lead(CONTACTED, ASSIGNEE);

        assertField("basis.confirmedOn", () -> lead.changeIntent(SHORT_TERM_RENT, Optional.empty(),
                new ConfirmationBasis(CREATED_ON.minusDays(1), "Owner confirmed")));
        assertEquals(LONG_TERM_RENT, lead.getIntent());
    }

    @Test
    void checkIntentChangeValues_validRequest_passes() {
        assertDoesNotThrow(() -> OwnerLead.checkIntentChangeValues(SHORT_TERM_RENT, Optional.of(night(500_000)),
                "Owner prefers holiday guests", new ConfirmationBasis(today(), "Owner said so on the phone")));
    }

    @Test
    void checkIntentChangeValues_eachBrokenValue_namesItsField() {
        ConfirmationBasis basis = basis();
        assertField("newIntent", () -> OwnerLead.checkIntentChangeValues(null, Optional.empty(), "r", basis));
        assertField("newIntent", () -> OwnerLead.checkIntentChangeValues(SALE, Optional.empty(), "r", basis));
        assertField("askingPrice.unit",
                () -> OwnerLead.checkIntentChangeValues(LONG_TERM_RENT, Optional.of(night(5)), "r", basis));
        for (String reason : new String[]{null, "", "   ", "x".repeat(501), "call 0905 123 456"}) {
            assertField("reason",
                    () -> OwnerLead.checkIntentChangeValues(SHORT_TERM_RENT, Optional.empty(), reason, basis));
        }
        assertField("basis", () -> OwnerLead.checkIntentChangeValues(SHORT_TERM_RENT, Optional.empty(), "r", null));
        assertField("basis.confirmedOn", () -> OwnerLead.checkIntentChangeValues(SHORT_TERM_RENT, Optional.empty(),
                "r", new ConfirmationBasis(null, "text")));
        assertField("basis.confirmedOn", () -> OwnerLead.checkIntentChangeValues(SHORT_TERM_RENT, Optional.empty(),
                "r", new ConfirmationBasis(today().plusDays(1), "text")));
        for (String text : new String[]{null, " ", "x".repeat(501), "+84.905.123.456"}) {
            assertField("basis.text", () -> OwnerLead.checkIntentChangeValues(SHORT_TERM_RENT, Optional.empty(),
                    "r", new ConfirmationBasis(today(), text)));
        }
    }

    @Test
    void checkIntentChangeValues_fiveHundredCharacters_areAccepted() {
        assertDoesNotThrow(() -> OwnerLead.checkIntentChangeValues(SHORT_TERM_RENT, Optional.empty(),
                "x".repeat(500), new ConfirmationBasis(today(), "y".repeat(500))));
    }

    @Test
    void confirmationBasis_requireValid_cleansTheText() {
        ConfirmationBasis clean = new ConfirmationBasis(CREATED_ON, "  café talk  ")
                .requireValid(CREATED_ON, today());

        assertEquals("café talk", clean.text());
        assertEquals(CREATED_ON, clean.confirmedOn());
    }

    @Test
    void invalidValue_messageNeverContainsTheSubmittedValue() {
        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> OwnerLead.checkIntentChangeValues(SHORT_TERM_RENT, Optional.empty(), "secret 0905123456",
                        basis()));

        assertFalse(ex.getMessage().contains("secret"));
    }

    private static LocalDate today() {
        return LocalDate.now(VIETNAM);
    }

    private static void assertField(String field, org.junit.jupiter.api.function.Executable executable) {
        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class, executable, field);
        assertEquals(field, ex.getField());
    }
}
