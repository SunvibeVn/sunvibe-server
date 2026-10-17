package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.model.lead.AskingPrice;
import vn.sunvibe.acquisition.core.domain.model.lead.GeoPoint;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadEntity;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Mapper to convert between the OwnerLead Domain Model and OwnerLeadEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database (rebuilt with OwnerLead.load).
 */
@Component
public class OwnerLeadEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public OwnerLead toDomain(OwnerLeadEntity entity) {
        if (entity == null) return null;

        // A stored point is already inside the served area and never a special-zone ward's, so load it as is
        Optional<GeoPoint> geoPoint = GeoPoint.of(entity.getLatitude(), entity.getLongitude(), false);
        Optional<AskingPrice> askingPrice = entity.getAskingPriceAmount() == null
                ? Optional.empty()
                : Optional.of(AskingPrice.of(entity.getAskingPriceAmount().longValueExact(), AskingPrice.CURRENCY,
                        entity.getAskingPriceUnit(), entity.getIntent()));

        return OwnerLead.load(
                OwnerLeadId.of(entity.getId()),
                RequestCode.of(entity.getRequestCode()),
                entity.getStatus(),
                entity.getIntent(),
                entity.getSource(),
                entity.getContactName(),
                PhoneNumber.parse(entity.getPhoneNormalized()),
                entity.getEmail(),
                entity.getProvinceCode(),
                entity.getProvinceName(),
                entity.getWardCode(),
                entity.getWardName(),
                entity.getAddressDetail(),
                entity.getAddressKey(),
                geoPoint,
                entity.getPropertyType(),
                askingPrice,
                entity.getAvailableFrom(),
                entity.getNotes(),
                entity.getPreferredCallbackTime(),
                ConsentRecordId.of(entity.getConsentRecordId()),
                entity.getSubmissionSnapshot(),
                entity.getAssignedUserId(),
                entity.getNextActionAt(),
                entity.getDuplicateOfLeadId() == null ? null : OwnerLeadId.of(entity.getDuplicateOfLeadId()),
                entity.getCloseReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    /**
     * Convert Domain → Entity.
     */
    public OwnerLeadEntity toEntity(OwnerLead domain) {
        if (domain == null) return null;

        return OwnerLeadEntity.builder()
                .id(domain.getId().getValue())
                .requestCode(domain.getRequestCode().getValue())
                .status(domain.getStatus())
                .intent(domain.getIntent())
                .source(domain.getSource())
                .contactName(domain.getContactName())
                .phoneNormalized(domain.getPhone().getValue())
                .email(domain.getEmail())
                .provinceCode(domain.getProvinceCode())
                .provinceName(domain.getProvinceName())
                .wardCode(domain.getWardCode())
                .wardName(domain.getWardName())
                .addressDetail(domain.getAddressDetail())
                .addressKey(domain.getAddressKey())
                .latitude(domain.getGeoPoint().map(GeoPoint::getLatitude).orElse(null))
                .longitude(domain.getGeoPoint().map(GeoPoint::getLongitude).orElse(null))
                .propertyType(domain.getPropertyType())
                .askingPriceAmount(domain.getAskingPrice().map(price -> BigDecimal.valueOf(price.getAmount()))
                        .orElse(null))
                .askingPriceUnit(domain.getAskingPrice().map(AskingPrice::getUnit).orElse(null))
                .availableFrom(domain.getAvailableFrom())
                .notes(domain.getNotes())
                .preferredCallbackTime(domain.getPreferredCallbackTime())
                .consentRecordId(domain.getConsentRecordId().getValue())
                .submissionSnapshot(domain.getSubmissionSnapshot())
                .assignedUserId(domain.getAssignedUserId())
                .nextActionAt(domain.getNextActionAt())
                .duplicateOfLeadId(domain.getDuplicateOfLeadId() == null ? null
                        : domain.getDuplicateOfLeadId().getValue())
                .closeReason(domain.getCloseReason())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }
}
