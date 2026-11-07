package vn.sunvibe.survey.core.testsupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import vn.sunvibe.survey.core.domain.model.media.MediaIncidentKind;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaIncidentDomainRepository;

/** Incident rows in memory (survey-media spec A-18): one open row per kind. */
public final class InMemoryMediaIncidents implements SurveyMediaIncidentDomainRepository {

    /** One incident row. */
    public record Row(MediaIncidentKind kind, Instant openedAt, Instant firstOverdueAt, int count,
                      Instant resolvedAt) {
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public synchronized void recordOverdue(MediaIncidentKind kind, Instant firstOverdueAt, int count,
                                           Instant appNow) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind() == kind && row.resolvedAt() == null) {
                Instant first = firstOverdueAt.isBefore(row.firstOverdueAt()) ? firstOverdueAt : row.firstOverdueAt();
                rows.set(i, new Row(kind, row.openedAt(), first, count, null));
                return;
            }
        }
        rows.add(new Row(kind, appNow, firstOverdueAt, count, null));
    }

    @Override
    public synchronized int resolveOpen(Instant appNow) {
        int resolved = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.resolvedAt() == null) {
                rows.set(i, new Row(row.kind(), row.openedAt(), row.firstOverdueAt(), row.count(), appNow));
                resolved++;
            }
        }
        return resolved;
    }

    public synchronized List<Row> open() {
        return rows.stream().filter(row -> row.resolvedAt() == null).toList();
    }

    public synchronized List<Row> all() {
        return List.copyOf(rows);
    }
}
