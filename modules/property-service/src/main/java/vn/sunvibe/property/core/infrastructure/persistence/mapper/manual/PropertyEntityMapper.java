package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.amenity.PropertyAmenityApply;
import vn.sunvibe.property.core.domain.model.document.PropertyDocument;
import vn.sunvibe.property.core.domain.model.enums.PropertyStatus;
import vn.sunvibe.property.core.domain.model.image.PropertyImage;
import vn.sunvibe.property.core.domain.model.rule.PropertyRuleApply;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper to convert between Property Domain Model and PropertyEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database.
 */
@Component
@RequiredArgsConstructor
public class PropertyEntityMapper {

    private final PropertyImageEntityMapper imageMapper;
    private final PropertyAmenityEntityMapper amenityMapper;
    private final PropertyRuleEntityMapper ruleMapper;
    private final PropertyDocumentEntityMapper documentMapper;

    /**
     * Convert Entity → Domain.
     * Called when fetching data from database.
     */
    public Property toDomain(PropertyEntity entity) {
        if (entity == null) return null;

        // Restore Value Objects from Entity
        PropertyId id = PropertyId.of(entity.getId());
        PropertyCode code = PropertyCode.of(entity.getPropertyCode());

        Location location = Location.of(
                entity.getStreet(),
                entity.getWard(),
                entity.getDistrict(),
                entity.getCity(),
                entity.getPostalCode(),
                entity.getLatitude(),
                entity.getLongitude()
        );

        Money monthlyPrice = Money.vnd(entity.getMonthlyPrice());
        Money depositPrice = Money.vnd(entity.getDepositPrice());

        // Create Domain Model
        Property domain = Property.load(
                id, code,
                entity.getTitle(),
                entity.getDescription(),
                location,
                entity.getType(),
                entity.getBedrooms(),
                entity.getBathrooms(),
                entity.getSquareMeters(),
                monthlyPrice,
                depositPrice,
                entity.getMinRentMonths(),
                entity.getOwnerId(),
                PropertyStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getVersion()
        );

        // Restore sub-entities if present
        if (entity.getImages() != null && !entity.getImages().isEmpty()) {
            entity.getImages().forEach(imageEntity -> {
                PropertyImage image = imageMapper.toDomain(imageEntity);
                domain.addImage(image);
            });
        }

        if (entity.getAmenities() != null && !entity.getAmenities().isEmpty()) {
            entity.getAmenities().forEach(amenityEntity -> {
                PropertyAmenityApply amenity = amenityMapper.toDomain(amenityEntity);
                domain.applyAmenity(amenity);
            });
        }

        if (entity.getRules() != null && !entity.getRules().isEmpty()) {
            entity.getRules().forEach(ruleEntity -> {
                PropertyRuleApply rule = ruleMapper.toDomain(ruleEntity);
                domain.applyRule(rule);
            });
        }

        if (entity.getDocuments() != null && !entity.getDocuments().isEmpty()) {
            entity.getDocuments().forEach(documentEntity -> {
                PropertyDocument document = documentMapper.toDomain(documentEntity);
                domain.addDocument(document);
            });
        }

        return domain;
    }

    /**
     * Convert Domain → Entity.
     * Called when saving to database.
     */
    public PropertyEntity toEntity(Property domain) {
        if (domain == null) return null;

        PropertyEntity entity = PropertyEntity.builder()
                .id(domain.getId().getValue())
                .propertyCode(domain.getPropertyCode().getValue())
                .title(domain.getTitle())
                .description(domain.getDescription())
                // Location fields
                .street(domain.getLocation().getStreet())
                .ward(domain.getLocation().getWard())
                .district(domain.getLocation().getDistrict())
                .city(domain.getLocation().getCity())
                .postalCode(domain.getLocation().getPostalCode())
                .latitude(domain.getLocation().getLatitude())
                .longitude(domain.getLocation().getLongitude())
                // Property details
                .type(domain.getType())
                .bedrooms(domain.getBedrooms())
                .bathrooms(domain.getBathrooms())
                .squareMeters(domain.getSquareMeters())
                // Money fields
                .monthlyPrice(domain.getMonthlyPrice().getAmount())
                .depositPrice(domain.getDepositPrice().getAmount())
                // Other fields
                .minRentMonths(domain.getMinRentMonths())
                .status(domain.getStatus().toString())
                .ownerId(domain.getOwnerId())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .createdBy(domain.getCreatedBy())
                .updatedBy(domain.getUpdatedBy())
                .version(domain.getVersion())
                .build();

        // Convert sub-entities
        if (domain.getImages() != null && !domain.getImages().isEmpty()) {
            List<PropertyImageEntity> imageEntities = domain.getImages().stream()
                    .map(imageMapper::toEntity)
                    .collect(Collectors.toList());
            entity.setImages(imageEntities);
        }

        if (domain.getAmenities() != null && !domain.getAmenities().isEmpty()) {
            List<PropertyAmenityEntity> amenityEntities = domain.getAmenities().stream()
                    .map(amenityMapper::toEntity)
                    .collect(Collectors.toList());
            entity.setAmenities(amenityEntities);
        }

        if (domain.getAppliedRules() != null && !domain.getAppliedRules().isEmpty()) {
            List<PropertyRuleEntity> ruleEntities = domain.getAppliedRules().stream()
                    .map(ruleMapper::toEntity)
                    .collect(Collectors.toList());
            entity.setRules(ruleEntities);
        }

        if (domain.getDocuments() != null && !domain.getDocuments().isEmpty()) {
            List<PropertyDocumentEntity> documentEntities = domain.getDocuments().stream()
                    .map(documentMapper::toEntity)
                    .collect(Collectors.toList());
            entity.setDocuments(documentEntities);
        }

        return entity;
    }

}