package vn.sunvibe.brokerage.core.presentation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftRequest;

/**
 * The strict reading of a draft's body (brokerage-agreement BR-8: the amount the owner confirms is the one sent; E05-S3
 * plan E05S3-P4). {@link BrokerageAgreementDraftRequest}, with its nested parts, is read with the application's JSON
 * mapper without {@code ACCEPT_FLOAT_AS_INT}, so a fraction sent for an amount, a count or the data version is a
 * malformed body instead of being cut. Unknown keys stay ignored (E05-S3 spec S3-D5). The converter only reads that type
 * and never writes; it is added ahead of the defaults and is not a bean, so Boot's own mapper and converter stay as they
 * are and every other body, the create's included, keeps them.
 */
@Configuration
public class StrictAgreementJsonConfig implements WebMvcConfigurer {

    private final JsonMapper applicationMapper;

    public StrictAgreementJsonConfig(JsonMapper applicationMapper) {
        this.applicationMapper = applicationMapper;
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(new StrictAgreementConverter(applicationMapper.rebuild()
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .build()));
    }

    /** Reads only the draft request. */
    static final class StrictAgreementConverter extends JacksonJsonHttpMessageConverter {

        StrictAgreementConverter(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return BrokerageAgreementDraftRequest.class.equals(type.toClass()) && super.canRead(type, mediaType);
        }

        @Override
        public boolean canRead(Class<?> clazz, MediaType mediaType) {
            return BrokerageAgreementDraftRequest.class.equals(clazz) && super.canRead(clazz, mediaType);
        }

        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
            return false;
        }

        @Override
        public boolean canWrite(Class<?> clazz, MediaType mediaType) {
            return false;
        }
    }
}
