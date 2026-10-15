package vn.sunvibe.auth.core.infrastructure.persistence.converter.manual;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.shared.crypto.CryptoService;

/**
 * Transparently encrypts a JPA entity field at rest and decrypts it on
 * read, delegating to {@link CryptoService}. Applied per-field via
 * {@code @Convert(converter = EncryptedStringAttributeConverter.class)}
 * — Spring Boot's JPA auto-configuration resolves {@code @Convert}
 * converters as Spring beans, so this class's {@link CryptoService}
 * dependency is injected normally.
 */
@Converter(autoApply = false)
@Component
@RequiredArgsConstructor
public class EncryptedStringAttributeConverter implements AttributeConverter<String, String> {

    private final CryptoService cryptoService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cryptoService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : cryptoService.decrypt(dbData);
    }
}
