package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.resumption.SurveyResumption;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyResumptionDomainRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hand-written fakes of the insert-only resumption and survey access-log repositories. */
public final class InMemorySurveyHistoryRepositories {

    private InMemorySurveyHistoryRepositories() {
    }

    /** Resumptions in the order saved. */
    public static class Resumptions implements SurveyResumptionDomainRepository {

        private final List<SurveyResumption> rows = new ArrayList<>();

        public List<SurveyResumption> all() {
            return List.copyOf(rows);
        }

        @Override
        public void save(SurveyResumption resumption) {
            rows.add(resumption);
        }

        @Override
        public List<SurveyResumption> findByLeadIdOrderByOccurredAt(String leadId) {
            return rows.stream().filter(row -> row.leadId().equals(leadId))
                    .sorted(Comparator.comparing(SurveyResumption::occurredAt)).toList();
        }
    }

    /** Survey access-log rows in the order saved. */
    public static class AccessLogs implements SurveyAccessLogDomainRepository {

        private final List<SurveyAccessLog> rows = new ArrayList<>();

        public List<SurveyAccessLog> all() {
            return List.copyOf(rows);
        }

        @Override
        public void save(SurveyAccessLog log) {
            rows.add(log);
        }

        @Override
        public List<SurveyAccessLog> findByLeadId(String leadId) {
            return rows.stream().filter(row -> row.leadId().equals(leadId)).toList();
        }
    }
}
