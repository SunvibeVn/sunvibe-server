package vn.sunvibe.shared.dto.response.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.logging.CorrelationId;

/** Error body JSON shape (ADR-010): old bodies stay byte-identical, new fields are additive (W9-17). */
class ErrorResponseJsonTest {

    private static final String TEST_ID = "01J0000000000000000000TEST";
    private final JsonMapper mapper = JsonMapper.builder().build();

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationId.MDC_KEY);
    }

    @Test
    void serialize_oldTwoFieldBody_isByteIdenticalToBefore() throws Exception {
        String json = mapper.writeValueAsString(
                new ErrorResponse<>("Validation failed", List.of(new ValidationError("title", "must not be blank"))));

        assertEquals(
                "{\"message\":\"Validation failed\",\"errors\":[{\"field\":\"title\",\"message\":\"must not be blank\"}]}",
                json);
    }

    @Test
    void serialize_newBody_hasFieldsInTheDocumentedOrder() throws Exception {
        MDC.put(CorrelationId.MDC_KEY, TEST_ID);

        String json = mapper.writeValueAsString(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "Validation failed.",
                List.of(ValidationError.of("title", "must not be blank"))));

        assertEquals("{\"code\":\"VALIDATION_FAILED\",\"message\":\"Validation failed.\","
                + "\"errors\":[{\"field\":\"title\",\"message\":\"must not be blank\"}],"
                + "\"correlationId\":\"" + TEST_ID + "\"}", json);
    }

    @Test
    void serialize_allowedValues_appearsOnlyWhenSet() throws Exception {
        String without = mapper.writeValueAsString(ValidationError.of("status", "invalid"));
        String with = mapper.writeValueAsString(ValidationError.withAllowed("status", "invalid", List.of("A", "B")));

        assertFalse(without.contains("allowedValues"));
        assertEquals("{\"field\":\"status\",\"message\":\"invalid\",\"allowedValues\":[\"A\",\"B\"]}", with);
    }

    @Test
    void serialize_emptyErrors_isEmptyArray() throws Exception {
        String json = mapper.writeValueAsString(
                ErrorResponse.of(ErrorCode.NOT_FOUND, "Not found.", List.<ValidationError>of()));

        assertTrue(json.contains("\"errors\":[]"));
    }

    @Test
    void of_noCorrelationIdInMdc_leavesCorrelationIdOut() throws Exception {
        String json = mapper.writeValueAsString(
                ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "Server error.", List.<ValidationError>of()));

        assertFalse(json.contains("correlationId"));
    }
}
