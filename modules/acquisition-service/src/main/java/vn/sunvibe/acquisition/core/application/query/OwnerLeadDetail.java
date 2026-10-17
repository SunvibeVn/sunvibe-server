package vn.sunvibe.acquisition.core.application.query;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One lead as a staff member who may see it opens it (acquisition-owner-lead-queue spec 6): the summary fields plus
 * the contact data, address, position, price and the rest of the form, the consent (no hashes), the duplicate
 * candidates the viewer can see and the history oldest first. {@code duplicateOfLeadId} is null unless the viewer can
 * see that lead; position and price are null when the owner gave none. Never the submission snapshot or a hash.
 */
public record OwnerLeadDetail(String id, String requestCode, OwnerLeadStatus status, OwnerLeadIntent intent,
                              String contactName, String provinceCode, String wardCode, String wardName,
                              OwnerLeadPropertyType propertyType, String assignedUserId, Instant nextActionAt,
                              boolean possibleDuplicate, Instant createdAt, long version, String phone, String email,
                              String addressDetail, BigDecimal latitude, BigDecimal longitude,
                              AskingPriceView askingPrice, LocalDate availableFrom, String notes,
                              String preferredCallbackTime, OwnerLeadSource source, String duplicateOfLeadId,
                              String closeReason, ConsentView consent,
                              List<DuplicateCandidateView> duplicateCandidates, List<LeadActivityView> activities) {

    /** The price the owner hopes to get, in whole VND per unit. */
    public record AskingPriceView(long amount, String currency, PriceUnit unit) {
    }

    public OwnerLeadDetail {
        duplicateCandidates = List.copyOf(duplicateCandidates);
        activities = List.copyOf(activities);
    }

    /** The detail holds contact data, address and position: never print it. */
    @Override
    public String toString() {
        return "OwnerLeadDetail[" + id + "]";
    }
}
