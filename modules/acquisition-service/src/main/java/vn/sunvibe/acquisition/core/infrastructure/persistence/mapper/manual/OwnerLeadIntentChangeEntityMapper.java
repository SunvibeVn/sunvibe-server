package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.lead.AskingPrice;
import vn.sunvibe.acquisition.core.domain.model.lead.ConfirmationBasis;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntentChange;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadIntentChangeEntity;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Mapper to convert between the OwnerLeadIntentChange Domain Model and OwnerLeadIntentChangeEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database (rebuilt with OwnerLeadIntentChange.load).
 */
@Component
public class OwnerLeadIntentChangeEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public OwnerLeadIntentChange toDomain(OwnerLeadIntentChangeEntity entity) {
        if (entity == null) return null;

        return OwnerLeadIntentChange.load(
                entity.getId(),
                OwnerLeadId.of(entity.getLeadId()),
                entity.getFromIntent(),
                price(entity.getFromPriceAmount(), entity.getFromPriceUnit(), entity.getFromIntent()),
                entity.getToIntent(),
                price(entity.getToPriceAmount(), entity.getToPriceUnit(), entity.getToIntent()),
                entity.getReason(),
                new ConfirmationBasis(entity.getBasisConfirmedOn(), entity.getBasisText()),
                entity.getActorUserId(),
                entity.getNewIntentCycle(),
                entity.getOccurredAt()
        );
    }

    /**
     * Convert Domain → Entity.
     */
    public OwnerLeadIntentChangeEntity toEntity(OwnerLeadIntentChange domain) {
        if (domain == null) return null;

        return OwnerLeadIntentChangeEntity.builder()
                .id(domain.getId())
                .leadId(domain.getLeadId().getValue())
                .fromIntent(domain.getFromIntent())
                .toIntent(domain.getToIntent())
                .fromPriceAmount(amount(domain.getFromPrice()))
                .fromPriceUnit(domain.getFromPrice().map(AskingPrice::getUnit).orElse(null))
                .toPriceAmount(amount(domain.getToPrice()))
                .toPriceUnit(domain.getToPrice().map(AskingPrice::getUnit).orElse(null))
                .reason(domain.getReason())
                .basisConfirmedOn(domain.getBasis().confirmedOn())
                .basisText(domain.getBasis().text())
                .actorUserId(domain.getActorUserId())
                .newIntentCycle(domain.getNewIntentCycle())
                .occurredAt(domain.getOccurredAt())
                .build();
    }

    private static Optional<AskingPrice> price(BigDecimal amount, PriceUnit unit, OwnerLeadIntent intent) {
        return amount == null ? Optional.empty()
                : Optional.of(AskingPrice.of(amount.longValueExact(), AskingPrice.CURRENCY, unit, intent));
    }

    private static BigDecimal amount(Optional<AskingPrice> price) {
        return price.map(value -> BigDecimal.valueOf(value.getAmount())).orElse(null);
    }
}
