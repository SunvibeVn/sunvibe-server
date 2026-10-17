package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.DuplicateCandidateDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hand-written fake of the duplicate links; records each saved pair. The visibility queries need to know who holds
 * each lead, so they work only when the fake is built with the lead repository of the test.
 */
public class InMemoryDuplicateCandidateRepository implements DuplicateCandidateDomainRepository {

    /** One saved pair: the lead, the lead it may repeat and when this was detected. */
    public record Link(OwnerLeadId leadId, OwnerLeadId candidateId, Instant detectedAt) {
    }

    private final List<Link> links = new ArrayList<>();
    private final OwnerLeadDomainRepository leads;

    /** A fake that only records pairs; the visibility queries throw. */
    public InMemoryDuplicateCandidateRepository() {
        this(null);
    }

    /** A fake whose visibility queries look the other lead of each pair up in the given lead repository. */
    public InMemoryDuplicateCandidateRepository(OwnerLeadDomainRepository leads) {
        this.leads = leads;
    }

    public List<Link> all() {
        return new ArrayList<>(links);
    }

    @Override
    public void saveAll(OwnerLeadId leadId, List<OwnerLeadId> candidateIds, Instant detectedAt) {
        candidateIds.forEach(candidateId -> links.add(new Link(leadId, candidateId, detectedAt)));
    }

    @Override
    public Set<String> leadIdsWithVisibleCandidate(Collection<String> leadIds,
                                                   Optional<String> visibleToUserIdOrEmptyForAll) {
        return leadIds.stream()
                .filter(leadId -> !visibleCandidates(OwnerLeadId.of(leadId), visibleToUserIdOrEmptyForAll).isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<OwnerLead> findVisibleCandidates(OwnerLeadId leadId, Optional<String> visibleToUserIdOrEmptyForAll) {
        return visibleCandidates(leadId, visibleToUserIdOrEmptyForAll);
    }

    private List<OwnerLead> visibleCandidates(OwnerLeadId leadId, Optional<String> visibleToUserIdOrEmptyForAll) {
        if (leads == null) {
            throw new IllegalStateException("Build the fake with the lead repository to use the visibility queries.");
        }
        return links.stream()
                .map(link -> otherSide(link, leadId))
                .flatMap(Optional::stream)
                .distinct()
                .map(leads::findById)
                .flatMap(Optional::stream)
                .filter(candidate -> visibleToUserIdOrEmptyForAll.isEmpty()
                        || visibleToUserIdOrEmptyForAll.get().equals(candidate.getAssignedUserId()))
                .sorted(Comparator.comparing(OwnerLead::getCreatedAt)
                        .thenComparing(candidate -> candidate.getId().getValue()))
                .toList();
    }

    private static Optional<OwnerLeadId> otherSide(Link link, OwnerLeadId leadId) {
        if (link.leadId().equals(leadId)) {
            return Optional.of(link.candidateId());
        }
        if (link.candidateId().equals(leadId)) {
            return Optional.of(link.leadId());
        }
        return Optional.empty();
    }
}
