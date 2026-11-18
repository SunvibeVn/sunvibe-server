package vn.sunvibe.brokerage.core.domain.repository;

import java.util.List;

/** One page of an agreement's history rows, newest first, and the number of its rows (E05-S7 spec S7-D10). */
public record AgreementHistoryPage(List<AgreementHistoryRow> rows, long totalElements) {

    public AgreementHistoryPage {
        rows = List.copyOf(rows);
    }
}
