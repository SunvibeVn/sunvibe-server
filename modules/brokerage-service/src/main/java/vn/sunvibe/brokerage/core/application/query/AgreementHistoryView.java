package vn.sunvibe.brokerage.core.application.query;

import java.util.List;

/**
 * One page of an agreement's history, newest first (E05-S7 spec S7-D7, section 4).
 *
 * @param page          the zero-based page answered
 * @param size          the size asked for
 * @param totalElements every stored event of the agreement
 */
public record AgreementHistoryView(List<AgreementHistoryEntryView> entries, int page, int size, long totalElements) {

    public AgreementHistoryView {
        entries = List.copyOf(entries);
    }
}
