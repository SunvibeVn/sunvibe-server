package vn.sunvibe.property.core.shared.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.infrastructure.persistence.entity.AmenityTypeEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.DocumentTypeEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.RuleCategoryEntity;
import vn.sunvibe.property.core.infrastructure.persistence.repository.AmenityTypeJpaRepository;
import vn.sunvibe.property.core.infrastructure.persistence.repository.DocumentTypeJpaRepository;
import vn.sunvibe.property.core.infrastructure.persistence.repository.RuleCategoryJpaRepository;

import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class PropertyMasterDataInitializer implements CommandLineRunner {

    private final RuleCategoryJpaRepository ruleCategoryRepo;
    private final AmenityTypeJpaRepository amenityTypeRepo;
    private final DocumentTypeJpaRepository documentTypeRepo;

    @Override
    public void run(String... args) {
        seedRuleCategories();
        seedAmenityTypes();
        seedDocumentTypes();
    }

    private void seedRuleCategories() {
        if (ruleCategoryRepo.count() > 0) return;

        ruleCategoryRepo.saveAll(List.of(
                RuleCategoryEntity.builder().id(1).categoryName("PETS")
                        .description("Pet policy").defaultSeverity("HIGH")
                        .isNegotiable(true).icon("🐾").build(),
                RuleCategoryEntity.builder().id(2).categoryName("SMOKING")
                        .description("Smoking policy").defaultSeverity("HIGH")
                        .isNegotiable(false).icon("🚬").build(),
                RuleCategoryEntity.builder().id(3).categoryName("NOISE")
                        .description("Noise policy").defaultSeverity("MEDIUM")
                        .isNegotiable(true).icon("🔊").build(),
                RuleCategoryEntity.builder().id(4).categoryName("GUESTS")
                        .description("Guest policy").defaultSeverity("LOW")
                        .isNegotiable(true).icon("👥").build(),
                RuleCategoryEntity.builder().id(5).categoryName("PARTIES")
                        .description("Party/event policy").defaultSeverity("HIGH")
                        .isNegotiable(false).icon("🎉").build(),
                RuleCategoryEntity.builder().id(6).categoryName("COOKING")
                        .description("Cooking policy").defaultSeverity("LOW")
                        .isNegotiable(true).icon("🍳").build()
        ));
        log.info("Seeded {} rule categories", ruleCategoryRepo.count());
    }

    private void seedAmenityTypes() {
        if (amenityTypeRepo.count() > 0) return;

        // Category entries (top-level, no parent)
        amenityTypeRepo.saveAll(List.of(
                AmenityTypeEntity.builder().id(1).amenityName("General Amenities")
                        .categoryId(null).categoryName(null)
                        .isStandard(true).levelOfNeed("STANDARD").build(),
                AmenityTypeEntity.builder().id(2).amenityName("Kitchen")
                        .categoryId(null).categoryName(null)
                        .isStandard(true).levelOfNeed("STANDARD").build(),
                AmenityTypeEntity.builder().id(3).amenityName("Bedroom")
                        .categoryId(null).categoryName(null)
                        .isStandard(true).levelOfNeed("STANDARD").build(),
                AmenityTypeEntity.builder().id(4).amenityName("Bathroom")
                        .categoryId(null).categoryName(null)
                        .isStandard(true).levelOfNeed("STANDARD").build()
        ));

        // Actual amenity types (reference a category)
        amenityTypeRepo.saveAll(List.of(
                AmenityTypeEntity.builder().id(10).amenityName("WiFi")
                        .categoryId(1).categoryName("General Amenities")
                        .description("Wireless internet connection").isStandard(true)
                        .levelOfNeed("STANDARD").icon("📶").build(),
                AmenityTypeEntity.builder().id(11).amenityName("Air Conditioner")
                        .categoryId(3).categoryName("Bedroom")
                        .description("Air conditioning").isStandard(true)
                        .levelOfNeed("STANDARD").icon("❄️").build(),
                AmenityTypeEntity.builder().id(12).amenityName("Washing Machine")
                        .categoryId(1).categoryName("General Amenities")
                        .description("Private washing machine").isStandard(false)
                        .levelOfNeed("PREMIUM").icon("🫧").build(),
                AmenityTypeEntity.builder().id(13).amenityName("Refrigerator")
                        .categoryId(2).categoryName("Kitchen")
                        .description("Full-size refrigerator").isStandard(true)
                        .levelOfNeed("STANDARD").icon("🧊").build(),
                AmenityTypeEntity.builder().id(14).amenityName("Gas/Electric Stove")
                        .categoryId(2).categoryName("Kitchen")
                        .description("Cooking stove").isStandard(true)
                        .levelOfNeed("STANDARD").icon("🔥").build(),
                AmenityTypeEntity.builder().id(15).amenityName("Water Heater")
                        .categoryId(4).categoryName("Bathroom")
                        .description("Hot and cold water").isStandard(true)
                        .levelOfNeed("STANDARD").icon("🚿").build(),
                AmenityTypeEntity.builder().id(16).amenityName("Parking")
                        .categoryId(1).categoryName("General Amenities")
                        .description("Motorbike/car parking space").isStandard(false)
                        .levelOfNeed("OPTIONAL").icon("🅿️").build(),
                AmenityTypeEntity.builder().id(17).amenityName("Balcony")
                        .categoryId(1).categoryName("General Amenities")
                        .description("Balcony/rooftop terrace").isStandard(false)
                        .levelOfNeed("OPTIONAL").icon("🌿").build()
        ));
        log.info("Seeded {} amenity types", amenityTypeRepo.count());
    }

    private void seedDocumentTypes() {
        if (documentTypeRepo.count() > 0) return;

        documentTypeRepo.saveAll(List.of(
                DocumentTypeEntity.builder().id(1).typeName("Land Use Rights Certificate")
                        .description("Red book / Pink book").requiresRenewal(false)
                        .isArchived(false).icon("📄").build(),
                DocumentTypeEntity.builder().id(2).typeName("Building Permit")
                        .description("Construction building permit").requiresRenewal(false)
                        .isArchived(false).icon("🏗️").build(),
                DocumentTypeEntity.builder().id(3).typeName("Fire Safety Certificate")
                        .description("Fire prevention and firefighting certificate")
                        .requiresRenewal(true).renewalFrequencyDays(365)
                        .isArchived(false).icon("🔥").build(),
                DocumentTypeEntity.builder().id(4).typeName("Home Insurance")
                        .description("Home insurance contract")
                        .requiresRenewal(true).renewalFrequencyDays(365)
                        .isArchived(false).icon("🛡️").build(),
                DocumentTypeEntity.builder().id(5).typeName("Land Lease Agreement")
                        .description("Land use rights lease contract")
                        .requiresRenewal(true).renewalFrequencyDays(1825)
                        .isArchived(false).icon("📋").build()
        ));
        log.info("Seeded {} document types", documentTypeRepo.count());
    }
}
