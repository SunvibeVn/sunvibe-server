package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository of the OwnerLead aggregate.
 */
public interface OwnerLeadDomainRepository {

    /**
     * Insert a new lead (version null) or update an existing one. An existing lead whose version is no longer the
     * stored one is refused with an optimistic locking failure. On success the lead carries the saved version
     * ({@link OwnerLead#markPersisted}): 0 after an insert, one more after an update.
     */
    void save(OwnerLead lead);

    Optional<OwnerLead> findById(OwnerLeadId id);

    /** The leads with these ids, in one query, in no particular order; unknown ids are skipped. */
    List<OwnerLead> findAllById(Collection<OwnerLeadId> ids);

    boolean existsByRequestCode(String requestCode);

    /**
     * The newest lead with this phone, intent, ward and address key created at or after the given time: a repeated
     * submission (acquisition-owner-intake BR-10).
     */
    Optional<OwnerLead> findRecentSameSubmission(String phoneNormalized, OwnerLeadIntent intent, String wardCode,
                                                 String addressKey, Instant createdAtOrAfter);

    /**
     * Ids of the leads of this phone that are still open ({@link OwnerLeadStatus#isOpen()}), without loading the
     * leads: the possible duplicates of a new submission (acquisition-owner-intake US-4).
     */
    List<OwnerLeadId> findOpenIdsByPhone(String phoneNormalized);

    /**
     * One page of the leads that match the criteria, newest first (created time, then id, both descending). The page
     * is zero-based; the result carries the number of matching leads over all pages.
     */
    PageResult<OwnerLead> search(LeadSearchCriteria criteria, int page, int size);
}
