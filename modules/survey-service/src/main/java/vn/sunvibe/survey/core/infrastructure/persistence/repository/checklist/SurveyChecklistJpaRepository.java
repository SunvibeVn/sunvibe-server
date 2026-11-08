package vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyChecklistJpaRepository extends JpaRepository<SurveyChecklistEntity, String> {

    /** The lead of a checklist without loading it (survey-checklist spec 5.5). */
    @Query("select c.leadId from SurveyChecklistEntity c where c.id = :id")
    Optional<String> findLeadIdById(@Param("id") String id);

    List<SurveyChecklistEntity> findByLeadIdOrderByChecklistNoAsc(String leadId);

    List<SurveyChecklistEntity> findByVisitAppointmentIdInOrderByChecklistNoAsc(Collection<String> visitIds);

    /** The row with a PostgreSQL {@code FOR SHARE} lock (survey-media spec 5.4). */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from SurveyChecklistEntity c where c.id = :id")
    Optional<SurveyChecklistEntity> findForShareById(@Param("id") String id);

    /** The row with a PostgreSQL {@code FOR NO KEY UPDATE} lock (survey-checklist submission spec 5.4, plan PD-8). */
    @Query(value = "select * from survey_checklists where id = :id for no key update", nativeQuery = true)
    Optional<SurveyChecklistEntity> findForNoKeyUpdateById(@Param("id") String id);

    /** Plan PD-4: the two arrays pair up by position, one bind each however many leads the viewer sees. */
    @Query(value = "select c.* from survey_checklists c"
            + " join unnest(cast(:leadIds as varchar[]), cast(:cycles as integer[])) as live(lead_id, intent_cycle)"
            + " on live.lead_id = c.lead_id and live.intent_cycle = c.intent_cycle"
            + " where c.status = 'SUBMITTED' order by c.submitted_at, c.id limit :size offset :offset",
            nativeQuery = true)
    List<SurveyChecklistEntity> findSubmittedPage(@Param("leadIds") String[] leadIds,
                                                  @Param("cycles") Integer[] cycles, @Param("size") int size,
                                                  @Param("offset") long offset);

    @Query(value = "select count(*) from survey_checklists c"
            + " join unnest(cast(:leadIds as varchar[]), cast(:cycles as integer[])) as live(lead_id, intent_cycle)"
            + " on live.lead_id = c.lead_id and live.intent_cycle = c.intent_cycle"
            + " where c.status = 'SUBMITTED'", nativeQuery = true)
    long countSubmitted(@Param("leadIds") String[] leadIds, @Param("cycles") Integer[] cycles);

    @Query("select coalesce(max(c.checklistNo), 0) + 1 from SurveyChecklistEntity c where c.leadId = :leadId")
    int nextChecklistNo(@Param("leadId") String leadId);
}
