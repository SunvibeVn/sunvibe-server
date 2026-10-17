package vn.sunvibe.acquisition.core.application.shared;

import com.github.f4b6a3.ulid.Ulid;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.exception.OwnerLeadNotFoundException;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;

import java.util.Optional;

/**
 * The only way staff operations look a lead up. A SALE sees only the leads assigned to them
 * (acquisition-owner-lead-queue BR-1), and a lead the viewer may not see is treated exactly like a lead that does not
 * exist (acquisition-owner-lead-queue BR-2), so a SALE never learns that a lead of someone else exists. An id that is
 * not a ULID is not looked up at all.
 */
@Component
public class LeadVisibility {

    private final OwnerLeadDomainRepository leads;

    public LeadVisibility(OwnerLeadDomainRepository leads) {
        this.leads = leads;
    }

    /**
     * The lead, or {@link OwnerLeadNotFoundException} when it is unknown, the id is not a lead id or the viewer may
     * not see it (one exception, one message for all three).
     */
    public OwnerLead requireVisible(LeadViewer viewer, String leadId) {
        return findVisible(viewer, leadId).orElseThrow(OwnerLeadNotFoundException::new);
    }

    /** The lead when it exists and the viewer may see it; empty otherwise, also for an id that is not a ULID. */
    public Optional<OwnerLead> findVisible(LeadViewer viewer, String leadId) {
        if (leadId == null || !Ulid.isValid(leadId)) {
            return Optional.empty();
        }
        return leads.findById(OwnerLeadId.of(leadId)).filter(viewer::canSee);
    }

    /**
     * The viewer as the duplicate-candidate queries take it: the viewer's id when they see only their own leads,
     * empty when they see every lead.
     */
    public static Optional<String> candidateViewer(LeadViewer viewer) {
        return viewer.access() == LeadAccess.ALL ? Optional.empty() : Optional.of(viewer.userId());
    }
}
