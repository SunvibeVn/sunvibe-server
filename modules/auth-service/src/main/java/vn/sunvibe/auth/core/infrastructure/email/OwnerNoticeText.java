package vn.sunvibe.auth.core.infrastructure.email;

import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The fixed text of the owner notices (property-create intake A-12; brokerage-agreement E05-S4 spec S4-D7): what
 * happened, when, and a closing line of its kind. Nothing comes from the request, so no code, lead, address, term or
 * amount can reach a mailbox. English, as the account page is (A-9).
 */
record OwnerNoticeText(String subject, String sentence, String closing) {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String IF_NOT_YOU = "If this was not you, contact Sunvibe and change your password.";
    private static final String IF_NOT_EXPECTED = "If you did not expect this, contact Sunvibe.";

    static OwnerNoticeText of(String kind) {
        return switch (OwnerNoticeKind.valueOf(kind)) {
            case OWNER_CONFIRMED -> new OwnerNoticeText("You confirmed a property link",
                    "Your Sunvibe account confirmed that you own a property.", IF_NOT_YOU);
            case OWNER_WITHDRAWN -> new OwnerNoticeText("You withdrew a property link",
                    "Your Sunvibe account withdrew a property confirmation.", IF_NOT_YOU);
            case OWNER_CHECK_REFUSED -> new OwnerNoticeText("A property link could not be confirmed",
                    "Sunvibe could not confirm a property link for your account.", IF_NOT_YOU);
            // brokerage-agreement BR-17: a neutral notice that only invites a sign-in to read the version.
            case AGREEMENT_PENDING -> new OwnerNoticeText("A brokerage agreement is waiting for you",
                    "Sunvibe sent you a brokerage agreement to read and answer."
                            + " Sign in to your Sunvibe account to read it.", IF_NOT_EXPECTED);
        };
    }

    String body(Instant occurredAt) {
        return sentence + " Time: " + WHEN.format(occurredAt) + " (Vietnam time). " + closing;
    }
}
