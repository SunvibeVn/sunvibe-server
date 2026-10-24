package vn.sunvibe.survey.core.presentation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistStartRequest;

/**
 * Strict checklist bodies (survey-checklist spec A-4, plan PD-2): the request records of
 * {@code ..presentation.dto.request.checklist} are read with the application's JSON mapper plus
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} and {@code FAIL_ON_NULL_FOR_PRIMITIVES}, so an unknown key (a {@code meta} or
 * server field sent back) is a 400 on its path, and without {@code ACCEPT_FLOAT_AS_INT}, so a fraction sent for an
 * amount, a count or a version is a 400 on its path instead of being cut (spec 7.1: JSON integers). The converter
 * only reads those types and never writes; it is added ahead of the defaults and is not a bean, so Boot's own mapper
 * and converter stay as they are and the other survey bodies keep ignoring unknown keys.
 */
@Configuration
public class StrictChecklistJsonConfig implements WebMvcConfigurer {

    static final String REQUEST_PACKAGE = ChecklistStartRequest.class.getPackageName();

    private final JsonMapper applicationMapper;

    public StrictChecklistJsonConfig(JsonMapper applicationMapper) {
        this.applicationMapper = applicationMapper;
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(new StrictChecklistConverter(applicationMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .build()));
    }

    /** Reads only the checklist request records. */
    static final class StrictChecklistConverter extends JacksonJsonHttpMessageConverter {

        StrictChecklistConverter(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return isChecklistRequest(type.toClass()) && super.canRead(type, mediaType);
        }

        @Override
        public boolean canRead(Class<?> clazz, MediaType mediaType) {
            return isChecklistRequest(clazz) && super.canRead(clazz, mediaType);
        }

        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
            return false;
        }

        @Override
        public boolean canWrite(Class<?> clazz, MediaType mediaType) {
            return false;
        }

        private static boolean isChecklistRequest(Class<?> type) {
            return type != null && REQUEST_PACKAGE.equals(type.getPackageName());
        }
    }
}
