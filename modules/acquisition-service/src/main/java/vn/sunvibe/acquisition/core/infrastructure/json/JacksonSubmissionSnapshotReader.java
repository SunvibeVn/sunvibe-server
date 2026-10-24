package vn.sunvibe.acquisition.core.infrastructure.json;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.acquisition.core.application.shared.SubmissionSnapshotReader;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * {@link SubmissionSnapshotReader} over the JSON the intake controller stores: only {@code askingPrice.amount} and
 * {@code askingPrice.unit} are read. A snapshot that is not JSON, has no price or a price that is not a whole number
 * gives no price; the caller logs the lead id only, never the snapshot.
 */
@Component
public class JacksonSubmissionSnapshotReader implements SubmissionSnapshotReader {

    private final ObjectMapper objectMapper;

    public JacksonSubmissionSnapshotReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SubmittedPrice> askingPrice(String snapshot) {
        if (snapshot == null) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(snapshot);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        JsonNode price = root.path("askingPrice");
        JsonNode amount = price.path("amount");
        JsonNode unit = price.path("unit");
        if (!amount.isNumber() || !unit.isString()) {
            return Optional.empty();
        }
        // The intake request writes a BigDecimal, which may come out as 1.5E+7; only a whole number is a price
        try {
            BigDecimal value = amount.decimalValue();
            return Optional.of(new SubmittedPrice(value.longValueExact(), unit.asString()));
        } catch (ArithmeticException e) {
            return Optional.empty();
        }
    }
}
