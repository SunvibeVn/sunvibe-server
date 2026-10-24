package vn.sunvibe.acquisition.core.testsupport;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.repository.LeadSearchCriteria;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.PageResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written fake of the lead repository, keyed by lead id. Like the real adapter it keeps its own copy of each
 * lead, gives the version back to the saved aggregate (0 on insert, one more on update) and refuses a stale version
 * with {@link ObjectOptimisticLockingFailureException}. Unlike the database it bumps the version on every update,
 * changed or not.
 */
public class InMemoryOwnerLeadRepository implements OwnerLeadDomainRepository {

    private final Map<OwnerLeadId, OwnerLead> leads = new LinkedHashMap<>();
    private final List<OwnerLeadId> shareLocks = new ArrayList<>();
    public int saveCount;

    /** The ids of each {@link #findByIdForShare} call, in call order. */
    public List<OwnerLeadId> shareLocks() {
        return new ArrayList<>(shareLocks);
    }

    @Override
    public Optional<OwnerLead> findByIdForShare(OwnerLeadId id) {
        shareLocks.add(id);
        return findById(id);
    }

    @Override
    public boolean existsById(OwnerLeadId id) {
        return leads.containsKey(id);
    }

    public List<OwnerLead> all() {
        return leads.values().stream().map(lead -> copy(lead, lead.getVersion())).toList();
    }

    @Override
    public void save(OwnerLead lead) {
        saveCount++;
        OwnerLead stored = leads.get(lead.getId());
        long newVersion;
        if (stored == null) {
            newVersion = 0L;
        } else if (lead.getVersion() == null || !lead.getVersion().equals(stored.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(OwnerLead.class, lead.getId().getValue());
        } else {
            newVersion = stored.getVersion() + 1;
        }
        leads.put(lead.getId(), copy(lead, newVersion));
        lead.markPersisted(newVersion);
    }

    @Override
    public Optional<OwnerLead> findById(OwnerLeadId id) {
        return Optional.ofNullable(leads.get(id)).map(lead -> copy(lead, lead.getVersion()));
    }

    @Override
    public List<OwnerLead> findAllById(Collection<OwnerLeadId> ids) {
        return ids.stream()
                .map(leads::get)
                .filter(java.util.Objects::nonNull)
                .map(lead -> copy(lead, lead.getVersion()))
                .toList();
    }

    @Override
    public boolean existsByRequestCode(String requestCode) {
        return byRequestCode(requestCode).isPresent();
    }

    /** Test helper: the stored lead with this request code. */
    public Optional<OwnerLead> byRequestCode(String requestCode) {
        return leads.values().stream()
                .filter(lead -> lead.getRequestCode().getValue().equals(requestCode))
                .findFirst()
                .map(lead -> copy(lead, lead.getVersion()));
    }

    @Override
    public Optional<OwnerLead> findRecentSameSubmission(String phoneNormalized, OwnerLeadIntent intent,
                                                        String wardCode, String addressKey,
                                                        Instant createdAtOrAfter) {
        return leads.values().stream()
                .filter(lead -> lead.getPhone().getValue().equals(phoneNormalized))
                .filter(lead -> lead.getIntent() == intent)
                .filter(lead -> lead.getWardCode().equals(wardCode))
                .filter(lead -> lead.getAddressKey().equals(addressKey))
                .filter(lead -> !lead.getCreatedAt().isBefore(createdAtOrAfter))
                .max(Comparator.comparing(OwnerLead::getCreatedAt))
                .map(lead -> copy(lead, lead.getVersion()));
    }

    @Override
    public List<OwnerLeadId> findOpenIdsByPhone(String phoneNormalized) {
        return new ArrayList<>(leads.values().stream()
                .filter(lead -> lead.getPhone().getValue().equals(phoneNormalized))
                .filter(lead -> lead.getStatus().isOpen())
                .map(OwnerLead::getId)
                .toList());
    }

    @Override
    public PageResult<OwnerLead> search(LeadSearchCriteria criteria, int page, int size) {
        List<OwnerLead> matching = leads.values().stream()
                .filter(lead -> criteria.assignedUserId().map(user -> user.equals(lead.getAssignedUserId())).orElse(true))
                .filter(lead -> criteria.statuses().isEmpty() || criteria.statuses().contains(lead.getStatus()))
                .filter(lead -> criteria.wardCode().map(ward -> ward.equals(lead.getWardCode())).orElse(true))
                .filter(lead -> criteria.requestCode()
                        .map(code -> code.equals(lead.getRequestCode().getValue())).orElse(true))
                .sorted(Comparator.comparing(OwnerLead::getCreatedAt).reversed()
                        .thenComparing(lead -> lead.getId().getValue(), Comparator.reverseOrder()))
                .toList();
        List<OwnerLead> items = matching.stream()
                .skip((long) page * size)
                .limit(size)
                .map(lead -> copy(lead, lead.getVersion()))
                .toList();
        return new PageResult<>(items, page, size, matching.size());
    }

    private static OwnerLead copy(OwnerLead lead, Long version) {
        return OwnerLead.load(lead.getId(), lead.getRequestCode(), lead.getStatus(), lead.getIntent(),
                lead.getSource(), lead.getContactName(), lead.getPhone(), lead.getEmail(), lead.getProvinceCode(),
                lead.getProvinceName(), lead.getWardCode(), lead.getWardName(), lead.getAddressDetail(),
                lead.getAddressKey(), lead.getGeoPoint(), lead.getPropertyType(), lead.getAskingPrice(),
                lead.getAvailableFrom(), lead.getNotes(), lead.getPreferredCallbackTime(), lead.getConsentRecordId(),
                lead.getSubmissionSnapshot(), lead.getAssignedUserId(), lead.getNextActionAt(),
                lead.getDuplicateOfLeadId(), lead.getCloseReason(), lead.getCreatedAt(), lead.getUpdatedAt(), version,
                lead.getIntentCycle());
    }
}
