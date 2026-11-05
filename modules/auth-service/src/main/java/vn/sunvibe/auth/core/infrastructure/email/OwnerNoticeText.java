package vn.sunvibe.auth.core.infrastructure.email;

import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The fixed text of the owner notices (property-create intake A-12): what happened, when, and what to do if it was not the
 * reader. Nothing comes from the request, so no code, lead or address can reach a mailbox. English, as the account page is
 * (A-9).
 */
record OwnerNoticeText(String subject, String sentence) {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String IF_NOT_YOU = "If this was not you, contact Sunvibe and change your password.";

    static OwnerNoticeText of(String kind) {
        return switch (OwnerNoticeKind.valueOf(kind)) {
            case OWNER_CONFIRMED -> new OwnerNoticeText("You confirmed a property link",
                    "Your Sunvibe account confirmed that you own a property.");
            case OWNER_WITHDRAWN -> new OwnerNoticeText("You withdrew a property link",
                    "Your Sunvibe account withdrew a property confirmation.");
            case OWNER_CHECK_REFUSED -> new OwnerNoticeText("A property link could not be confirmed",
                    "Sunvibe could not confirm a property link for your account.");
        };
    }

    String body(Instant occurredAt) {
        return sentence + " Time: " + WHEN.format(occurredAt) + " (Vietnam time). " + IF_NOT_YOU;
    }
}
