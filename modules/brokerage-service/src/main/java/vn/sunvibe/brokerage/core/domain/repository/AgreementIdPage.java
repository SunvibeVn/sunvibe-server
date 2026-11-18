package vn.sunvibe.brokerage.core.domain.repository;

import java.util.List;

/**
 * One page of agreement ids in the list's order, and the number of matches over every page (E05-S7 spec S7-D10).
 */
public record AgreementIdPage(List<String> agreementIds, long totalElements) {

    public AgreementIdPage {
        agreementIds = List.copyOf(agreementIds);
    }
}
