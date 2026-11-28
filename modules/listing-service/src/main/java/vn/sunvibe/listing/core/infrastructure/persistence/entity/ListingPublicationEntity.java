package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.Furnishing;
import vn.sunvibe.listing.core.domain.model.listing.Pets;
import vn.sunvibe.listing.core.domain.model.listing.ShareMode;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity - the publication of a listing (listing-publish E06-S7 spec 5.3, E06S7-D2): the public data of its live
 * revision and the internal columns {@code listing_id}, {@code unit_id}, {@code agreement_id}, {@code terms_digest} and
 * {@code row_version}. Written only by the put live, as a delete and an insert, never merged; its fee rows are
 * {@link ListingPublicationFeeEntity}. {@link #toString()} shows the listing id only.
 */
@Entity
@Table(name = "listing_publications")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingPublicationEntity {

    @Id
    @Column(name = "listing_id", length = 26)
    private String listingId;

    @Column(name = "code", nullable = false, length = 12)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "property_type", length = 20)
    private String propertyType;

    @Column(name = "province_code", length = 10)
    private String provinceCode;

    @Column(name = "ward_code", length = 10)
    private String wardCode;

    @Column(name = "bedrooms", nullable = false)
    private int bedrooms;

    @Column(name = "bathrooms", nullable = false)
    private int bathrooms;

    @Column(name = "area_sqm", nullable = false)
    private double areaSqm;

    @Column(name = "monthly_rent", nullable = false, precision = 15, scale = 0)
    private BigDecimal monthlyRent;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_basis", nullable = false, length = 6)
    private DepositBasis depositBasis;

    @Column(name = "deposit_months")
    private Integer depositMonths;

    @Column(name = "deposit_amount", precision = 15, scale = 0)
    private BigDecimal depositAmount;

    @Column(name = "minimum_term_months", nullable = false)
    private int minimumTermMonths;

    @Column(name = "payment_period_months", nullable = false)
    private int paymentPeriodMonths;

    @Column(name = "title", nullable = false, length = 90)
    private String title;

    @Column(name = "description", nullable = false, length = 3000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_furnishing", length = 5)
    private Furnishing featureFurnishing;

    @Column(name = "feature_air_conditioner", nullable = false)
    private boolean featureAirConditioner;

    @Column(name = "feature_water_heater", nullable = false)
    private boolean featureWaterHeater;

    @Column(name = "feature_fridge", nullable = false)
    private boolean featureFridge;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_washing_machine", length = 7)
    private ShareMode featureWashingMachine;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_kitchen", length = 7)
    private ShareMode featureKitchen;

    @Column(name = "feature_balcony", nullable = false)
    private boolean featureBalcony;

    @Column(name = "feature_elevator", nullable = false)
    private boolean featureElevator;

    @Column(name = "feature_motorbike_parking", nullable = false)
    private boolean featureMotorbikeParking;

    @Column(name = "feature_car_parking", nullable = false)
    private boolean featureCarParking;

    @Column(name = "feature_internet", nullable = false)
    private boolean featureInternet;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_pets", length = 11)
    private Pets featurePets;

    @Column(name = "feature_no_shared_landlord", nullable = false)
    private boolean featureNoSharedLandlord;

    @Column(name = "went_live_at", nullable = false)
    private Instant wentLiveAt;

    @Column(name = "unit_id", nullable = false, length = 26)
    private String unitId;

    @Column(name = "agreement_id", nullable = false, length = 26)
    private String agreementId;

    @Column(name = "terms_digest", nullable = false, length = 64)
    private String termsDigest;

    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Override
    public String toString() {
        return "ListingPublicationEntity[" + listingId + "]";
    }
}
