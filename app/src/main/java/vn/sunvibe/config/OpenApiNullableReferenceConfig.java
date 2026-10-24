package vn.sunvibe.config;

import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * springdoc writes {@code @Schema(nullable = true)} on a property that refers to another schema as {@code "type":
 * "null"} next to the {@code $ref}; in OpenAPI 3.1 the two combine, so the property would allow neither the object
 * nor null. This customizer writes such a property as {@code anyOf} of the reference and {@code {"type": "null"}},
 * keeping its description, so the contract and the generated clients say "that schema or null" (for example
 * {@code askingPrice} of a requirement change, survey-schedule spec 7.1). Scalar nullable properties are already right
 * ({@code "type": ["string", "null"]}) and are left alone. It rewrites the top-level properties of the component
 * schemas, the only place such properties occur; {@code OpenApiNullableReferenceTest} fails if one appears anywhere
 * else. Assumes the OpenAPI 3.1 output springdoc writes by default ({@code {"type": "null"}} is 3.1 only).
 */
@Configuration
public class OpenApiNullableReferenceConfig {

    private static final String NULL_TYPE = "null";

    @Bean
    public OpenApiCustomizer nullableReferenceCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
                @SuppressWarnings("rawtypes")
                Map<String, Schema> properties = schema.getProperties();
                if (properties != null) {
                    properties.replaceAll((name, property) -> isNullableReference(property)
                            ? referenceOrNull(property) : property);
                }
            }
        };
    }

    private static boolean isNullableReference(Schema<?> property) {
        if (property.get$ref() == null) {
            return false;
        }
        Set<String> types = property.getTypes();
        return NULL_TYPE.equals(property.getType()) || (types != null && types.contains(NULL_TYPE))
                || Boolean.TRUE.equals(property.getNullable());
    }

    /**
     * Only the description is carried over; a property with any other keyword beside the reference (read-only,
     * deprecated, example, ...) stops the contract build instead of losing it silently.
     */
    private static Schema<Object> referenceOrNull(Schema<?> property) {
        if (property.getTitle() != null || property.getReadOnly() != null || property.getWriteOnly() != null
                || property.getDeprecated() != null || property.getExample() != null || property.getExamples() != null
                || property.getDefault() != null || property.getExtensions() != null) {
            throw new IllegalStateException("A nullable schema reference carries a keyword this customizer would drop: "
                    + property.get$ref());
        }
        Schema<Object> reference = new Schema<>().specVersion(SpecVersion.V31).$ref(property.get$ref());
        Schema<Object> nullValue = new Schema<>().specVersion(SpecVersion.V31)
                .types(new LinkedHashSet<>(List.of(NULL_TYPE)));
        Schema<Object> either = new Schema<>().specVersion(SpecVersion.V31);
        either.setDescription(property.getDescription());
        either.setAnyOf(List.of(reference, nullValue));
        return either;
    }
}
