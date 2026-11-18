package vn.sunvibe.brokerage.core.application.query;

import java.util.List;

/**
 * One page of the staff agreement list (E05-S7 spec S7-D1, section 4).
 *
 * @param page          the zero-based page answered
 * @param size          the size asked for
 * @param totalElements every agreement in the caller's scope that matches
 */
public record AgreementPageView(List<AgreementListItemView> items, int page, int size, long totalElements) {

    public AgreementPageView {
        items = List.copyOf(items);
    }
}
