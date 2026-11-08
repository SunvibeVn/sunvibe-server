package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

/**
 * A stored lead activity keeps every value in its own field through the row and back (cleanup spec A-5, SV-169):
 * each parameter of {@link OwnerLeadActivity#load} gets a value no other parameter has, so two arguments swapped in
 * the mapper or in {@code load} fail here.
 */
class OwnerLeadActivityEntityMapperTest {

    private final OwnerLeadActivityEntityMapper mapper = new OwnerLeadActivityEntityMapper();

    @Test
    void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
        OwnerLeadActivity stored = OwnerLeadActivity.load(201L, OwnerLeadId.of("lead-1"),
                OwnerLeadActivityType.REASSIGNED, "actor-1", OwnerLeadStatus.CONTACTED,
                OwnerLeadStatus.SURVEY_SCHEDULED, "from-1", "to-1", "note-1", at(1), at(2), "ref-1", 7,
                "detail-1");

        OwnerLeadActivity back = mapper.toDomain(mapper.toEntity(stored));

        assertEquals(201L, back.getId());
        assertEquals("lead-1", back.getLeadId().getValue());
        assertEquals(OwnerLeadActivityType.REASSIGNED, back.getType());
        assertEquals("actor-1", back.getActorUserId());
        assertEquals(OwnerLeadStatus.CONTACTED, back.getFromStatus());
        assertEquals(OwnerLeadStatus.SURVEY_SCHEDULED, back.getToStatus());
        assertEquals("from-1", back.getFromAssigneeId());
        assertEquals("to-1", back.getToAssigneeId());
        assertEquals("note-1", back.getNote());
        assertEquals(at(1), back.getNextActionAt());
        assertEquals(at(2), back.getOccurredAt());
        assertEquals("ref-1", back.getReferenceId());
        assertEquals(7, back.getReferenceNo());
        assertEquals("detail-1", back.getReferenceDetail());
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-11-01T01:00:00Z").plusSeconds(60L * minute);
    }
}
