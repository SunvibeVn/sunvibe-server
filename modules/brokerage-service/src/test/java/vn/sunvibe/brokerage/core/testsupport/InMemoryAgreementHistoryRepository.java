package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * History rows by agreement, paged newest first as the SQL does: by time descending, the later added first on equal
 * times (the row id grows with each insert). Every page read is counted.
 */
public final class InMemoryAgreementHistoryRepository implements AgreementHistoryDomainRepository {

    private final Map<String, List<AgreementHistoryRow>> rows = new HashMap<>();
    private int calls;

    /** Adds a row as if an earlier transaction had stored it; not counted as a call. */
    public void add(String agreementId, AgreementHistoryRow row) {
        rows.computeIfAbsent(agreementId, id -> new ArrayList<>()).add(row);
    }

    @Override
    public AgreementHistoryPage history(String agreementId, int page, int size) {
        calls++;
        List<AgreementHistoryRow> stored = rows.getOrDefault(agreementId, List.of());
        List<AgreementHistoryRow> newestFirst = new ArrayList<>();
        for (int index = stored.size() - 1; index >= 0; index--) {
            newestFirst.add(stored.get(index));
        }
        // A stable sort keeps the later added first among rows of equal times.
        newestFirst.sort((left, right) -> right.occurredAt().compareTo(left.occurredAt()));
        int from = Math.min(page * size, newestFirst.size());
        int to = Math.min(from + size, newestFirst.size());
        return new AgreementHistoryPage(newestFirst.subList(from, to), newestFirst.size());
    }

    /** How many pages were read. */
    public int calls() {
        return calls;
    }
}
