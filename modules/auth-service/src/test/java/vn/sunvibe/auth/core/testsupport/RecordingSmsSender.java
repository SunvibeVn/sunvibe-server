package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps "sent" SMS in memory; {@link #lastCode()} extracts the 6-digit code. */
public class RecordingSmsSender implements SmsSenderPort {

    public record Sms(String phoneNumber, String message) {
    }

    public final List<Sms> sent = new ArrayList<>();

    @Override
    public void send(String phoneNumber, String message) {
        sent.add(new Sms(phoneNumber, message));
    }

    public String lastCode() {
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(sent.getLast().message());
        if (!matcher.find()) {
            throw new AssertionError("No 6-digit code in: " + sent.getLast().message());
        }
        return matcher.group(1);
    }
}
