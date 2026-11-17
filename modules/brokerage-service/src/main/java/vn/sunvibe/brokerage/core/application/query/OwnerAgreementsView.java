package vn.sunvibe.brokerage.core.application.query;

import java.util.List;

/**
 * The owner's agreements with a version sent to them (E05-S5 spec S5-D1): at most 50, newest sending first.
 *
 * @param more whether the owner holds more such agreements than the answer shows
 */
public record OwnerAgreementsView(List<OwnerAgreementView> items, boolean more) {

    public OwnerAgreementsView {
        items = List.copyOf(items);
    }

    /** The confirmation statement the owner reads, with its version (brokerage-agreement BR-10; spec S5-D3). */
    public record StatementView(String version, String text) {
    }
}
