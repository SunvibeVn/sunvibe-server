package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import vn.sunvibe.common.dto.response.error.BaseErrorModel;
import vn.sunvibe.common.dto.response.error.ErrorResponse;
import vn.sunvibe.property.core.presentation.controller.property.PropertyController;
import vn.sunvibe.property.core.presentation.dto.request.PropertyCreateRequest;
import vn.sunvibe.property.core.presentation.dto.response.PropertyCreateResponse;

class PropertyControllerOpenApiTest {

    @Test
    void createProperty_documentsTheActualResponseSchemas() throws Exception {
        Method method = PropertyController.class.getDeclaredMethod("createProperty", PropertyCreateRequest.class);
        ApiResponses annotation = method.getAnnotation(ApiResponses.class);
        assertNotNull(annotation);

        Map<String, ApiResponse> responses = Arrays.stream(annotation.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, Function.identity()));

        assertSchema(responses.get("201"), PropertyCreateResponse.class);
        assertEquals(java.util.List.of(ErrorResponse.class, BaseErrorModel.class),
                Arrays.asList(responses.get("400").content()[0].schema().oneOf()));
        assertEquals("object", responses.get("401").content()[0].schema().type());
        assertHasContent(responses.get("403"));
        assertSchema(responses.get("409"), ErrorResponse.class);
        assertSchema(responses.get("422"), ErrorResponse.class);
    }

    private static void assertSchema(ApiResponse response, Class<?> expected) {
        assertNotNull(response);
        assertEquals(1, response.content().length);
        Schema schema = response.content()[0].schema();
        assertNotNull(schema);
        assertEquals(expected, schema.implementation());
    }

    private static void assertHasContent(ApiResponse response) {
        assertNotNull(response);
        assertTrue(response.content().length > 0);
        Content content = response.content()[0];
        assertNotNull(content.schema());
    }
}
