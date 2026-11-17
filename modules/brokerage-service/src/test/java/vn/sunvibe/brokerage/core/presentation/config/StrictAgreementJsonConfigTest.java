package vn.sunvibe.brokerage.core.presentation.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementCreateRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementTermsInput;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strict reading of the draft body (E05-S3 plan E05S3-P4): only the draft request goes through the strict
 * converter, and it never writes, so every other body and every answer keep Boot's converter.
 */
class StrictAgreementJsonConfigTest {

    private final StrictAgreementJsonConfig.StrictAgreementConverter converter =
            new StrictAgreementJsonConfig.StrictAgreementConverter(JsonMapper.builder().build());

    @Test
    void strictConverter_readsOnlyTheDraftRequestAndWritesNothing() {
        assertTrue(converter.canRead(BrokerageAgreementDraftRequest.class, MediaType.APPLICATION_JSON));
        assertTrue(converter.canRead(ResolvableType.forClass(BrokerageAgreementDraftRequest.class),
                MediaType.APPLICATION_JSON));

        for (Class<?> other : List.of(BrokerageAgreementCreateRequest.class, BrokerageAgreementTermsInput.class)) {
            assertFalse(converter.canRead(other, MediaType.APPLICATION_JSON), other.getName());
            assertFalse(converter.canRead(ResolvableType.forClass(other), MediaType.APPLICATION_JSON), other.getName());
        }
        for (Class<?> type : List.of(BrokerageAgreementDraftRequest.class, BrokerageAgreementCreateRequest.class,
                BrokerageAgreementTermsInput.class)) {
            assertFalse(converter.canWrite(type, MediaType.APPLICATION_JSON), type.getName());
            assertFalse(converter.canWrite(ResolvableType.forClass(type), type, MediaType.APPLICATION_JSON),
                    type.getName());
        }
    }
}
