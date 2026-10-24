package vn.sunvibe.acquisition.core.infrastructure.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.acquisition.core.application.shared.SubmissionSnapshotReader.SubmittedPrice;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The asking price of the owner's own submission, read from the stored intake request (survey-checklist spec A-18,
 * A-27). Nothing else of the snapshot is read; a snapshot it cannot read gives no price.
 */
class JacksonSubmissionSnapshotReaderTest {

    private final JacksonSubmissionSnapshotReader reader = new JacksonSubmissionSnapshotReader(
            JsonMapper.builder().build());

    /** The shape OwnerIntakeController writes: the whole intake request as the owner sent it. */
    @Test
    void askingPrice_intakeSnapshot_returnsAmountAndUnit() {
        String snapshot = "{\"contactName\":\"Nguyễn Thị Lan\",\"phone\":\"0909123456\",\"email\":null,"
                + "\"intent\":\"LONG_TERM_RENT\",\"provinceCode\":\"48\",\"wardCode\":\"20242\","
                + "\"addressDetail\":\"12 Bạch Đằng\",\"latitude\":16.0678,\"longitude\":108.2208,"
                + "\"propertyType\":\"APARTMENT\",\"askingPrice\":{\"amount\":15000000,\"currency\":\"VND\","
                + "\"unit\":\"MONTH\"},\"availableFrom\":\"2026-11-01\",\"notes\":null,"
                + "\"preferredCallbackTime\":null,\"consent\":{\"accepted\":true,\"noticeVersion\":\"v1\"}}";

        assertEquals(Optional.of(new SubmittedPrice(15_000_000L, "MONTH")), reader.askingPrice(snapshot));
    }

    @Test
    void askingPrice_amountWrittenWithAnExponent_isTheWholeNumber() {
        assertEquals(Optional.of(new SubmittedPrice(15_000_000L, "MONTH")),
                reader.askingPrice("{\"askingPrice\":{\"amount\":1.5E+7,\"currency\":\"VND\",\"unit\":\"MONTH\"}}"));
    }

    @Test
    void askingPrice_noAskingPrice_isEmpty() {
        assertEquals(Optional.empty(), reader.askingPrice("{\"contactName\":\"Lan\"}"));
        assertEquals(Optional.empty(), reader.askingPrice("{\"askingPrice\":null}"));
    }

    @Test
    void askingPrice_malformedJson_isEmpty() {
        assertEquals(Optional.empty(), reader.askingPrice("not json"));
        assertEquals(Optional.empty(), reader.askingPrice("{\"askingPrice\":{\"amount\":\"many\",\"unit\":\"MONTH\"}}"));
        assertEquals(Optional.empty(), reader.askingPrice("{\"askingPrice\":{\"amount\":1500.5,\"unit\":\"MONTH\"}}"));
        assertEquals(Optional.empty(), reader.askingPrice("{\"askingPrice\":{\"amount\":15000000}}"));
        assertEquals(Optional.empty(), reader.askingPrice("[]"));
        assertEquals(Optional.empty(), reader.askingPrice(null));
    }
}
