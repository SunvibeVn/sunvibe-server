package vn.sunvibe.property.core.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.command.PropertyCreateCommand;
import vn.sunvibe.property.core.application.exception.PropertyAlreadyExistsException;
import vn.sunvibe.property.core.application.exception.PropertyServiceException;
import vn.sunvibe.property.core.application.usecase.PropertyCreateUseCase;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.amenity.AmenityType;
import vn.sunvibe.property.core.domain.model.amenity.PropertyAmenityApply;
import vn.sunvibe.property.core.domain.model.image.PropertyImage;
import vn.sunvibe.property.core.domain.model.rule.PropertyRuleApply;
import vn.sunvibe.property.core.domain.model.rule.RuleCategory;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.repository.AmenityTypeDomainRepository;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;
import vn.sunvibe.property.core.domain.repository.RuleCategoryDomainRepository;

import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PropertyCreateService implements PropertyCreateUseCase {

    private final PropertyDomainRepository propertyDomainRepository;
    private final RuleCategoryDomainRepository ruleCategoryDomainRepository;
    private final AmenityTypeDomainRepository amenityTypeDomainRepository;

    @Override
    public PropertyId execute(PropertyCreateCommand command) {
        if (command == null || !command.isValid()) {
            throw new PropertyServiceException("Command is null or missing required fields");
        }

        log.info("Creating property: code={}", command.propertyCode());

        try {
            PropertyCode propertyCode = PropertyCode.of(command.propertyCode());
            if (propertyDomainRepository.existsByCode(propertyCode)) {
                throw new PropertyAlreadyExistsException("Property code already exists: " + command.propertyCode());
            }

            Property property = Property.create(
                    propertyCode,
                    command.title(),
                    command.description(),
                    Location.of(command.street(), command.ward(), command.district(),
                            command.city(), command.postalCode(), command.latitude(), command.longitude()),
                    command.propertyType(),
                    command.bedrooms(),
                    command.bathrooms(),
                    command.squareMeters(),
                    Money.vnd(command.monthlyPrice()),
                    Money.vnd(command.depositPrice()),
                    command.minRentMonths(),
                    command.ownerId(),
                    command.createdBy()
            );

            applyRules(property, command.ruleCategories());
            applyAmenities(property, command.amenityTypes());
            addImages(property, command.images(), command.createdBy());

            propertyDomainRepository.save(property);
            log.info("Property saved: id={}", property.getId());

            return property.getId();

        } catch (PropertyServiceException | PropertyAlreadyExistsException e) {
            log.warn("Validation error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating property", e);
            throw new PropertyServiceException("Failed to create property: " + e.getMessage(), e);
        }
    }

    private void applyRules(Property property, List<Integer> ruleCategoryIds) {
        for (Integer id : ruleCategoryIds) {
            RuleCategory ruleCategory = ruleCategoryDomainRepository.findById(id)
                    .orElseThrow(() -> new PropertyServiceException("RuleCategory not found: " + id));
            property.applyRule(PropertyRuleApply.createDefault(property.getId(), ruleCategory));
        }
    }

    private void applyAmenities(Property property, List<Integer> amenityTypeIds) {
        for (Integer id : amenityTypeIds) {
            AmenityType amenityType = amenityTypeDomainRepository.findById(id)
                    .orElseThrow(() -> new PropertyServiceException("AmenityType not found: " + id));
            property.applyAmenity(PropertyAmenityApply.included(property.getId(), amenityType));
        }
    }

    private void addImages(Property property, List<PropertyCreateCommand.ImageData> images, String uploadedBy) {
        for (PropertyCreateCommand.ImageData data : images) {
            PropertyImage image = data.isPrimary()
                    ? PropertyImage.createPrimary(property.getId(), data.imageUrl(), data.imageKey(), data.caption(), uploadedBy)
                    : PropertyImage.create(property.getId(), data.imageUrl(), data.imageKey(), data.caption(), data.displayOrder(), uploadedBy);
            property.addImage(image);
        }
    }
}
