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
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.ShareMode;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - one revision of a listing (listing-publish E06-S1 spec 5.3; E06-S2 spec 5.3), kept beside the listing's
 * entity. Every column is mapped both ways, so a changed revision is written by id as a whole (E06S2-D19); its fee rows
 * are {@link ListingRevisionFeeEntity}, written by their own repository. Its content and terms are unpublished
 * (listing-publish US-10 AC-4), so {@link #toString()} shows the id only.
 */
@Entity
@Table(name = "listing_revisions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingRevisionEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "listing_id", nullable = false, length = 26)
    private String listingId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RevisionStatus status;

    @Column(name = "created_by", nullable = false, length = 26)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "title", length = 90)
    private String title;

    @Column(name = "description", length = 3000)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_source", length = 13)
    private TermsSource termsSource;

    @Column(name = "terms_pending_start_date")
    private LocalDate termsPendingStartDate;

    @Column(name = "terms_captured_at")
    private Instant termsCapturedAt;

    @Column(name = "terms_monthly_rent", precision = 15, scale = 0)
    private BigDecimal termsMonthlyRent;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_deposit_basis", length = 6)
    private DepositBasis termsDepositBasis;

    @Column(name = "terms_deposit_months")
    private Integer termsDepositMonths;

    @Column(name = "terms_deposit_amount", precision = 15, scale = 0)
    private BigDecimal termsDepositAmount;

    @Column(name = "terms_minimum_term_months")
    private Integer termsMinimumTermMonths;

    @Column(name = "terms_payment_period_months")
    private Integer termsPaymentPeriodMonths;

    @Column(name = "unit_asset_type", length = 20)
    private String unitAssetType;

    @Column(name = "unit_province_code", length = 10)
    private String unitProvinceCode;

    @Column(name = "unit_province_name", length = 100)
    private String unitProvinceName;

    @Column(name = "unit_ward_code", length = 10)
    private String unitWardCode;

    @Column(name = "unit_ward_name", length = 100)
    private String unitWardName;

    @Column(name = "unit_bedrooms")
    private Integer unitBedrooms;

    @Column(name = "unit_bathrooms")
    private Integer unitBathrooms;

    @Column(name = "unit_area_sqm")
    private Double unitAreaSqm;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by", length = 26)
    private String submittedBy;

    /** The number of the revision this one was copied from (listing-publish E06-S4 spec E06S4-D14); null otherwise. */
    @Column(name = "copied_from_revision_no")
    private Integer copiedFromRevisionNo;

    /** The time the revision went live (listing-publish E06-S6 spec E06S6-D6); set for a Live or Replaced one only. */
    @Column(name = "went_live_at")
    private Instant wentLiveAt;

    /** The time the revision was replaced by a newer live one (E06S6-D6); set for a Replaced one only. */
    @Column(name = "replaced_at")
    private Instant replacedAt;

    /**
     * Whether the revision's fee names passed the machine check at its submission (listing-publish E06-S7 spec
     * E06S7-D11); false on every row stored before it.
     */
    @Column(name = "fee_names_checked", nullable = false)
    private boolean feeNamesChecked;

    @Override
    public String toString() {
        return "ListingRevisionEntity[" + id + "]";
    }
}
