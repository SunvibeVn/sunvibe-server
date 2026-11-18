package vn.sunvibe.brokerage.core.application.command;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Draft a brokerage agreement for one unit of the lead's intake basis (brokerage-agreement US-1 AC-3). The transaction
 * type stays a text here: the command refuses an unknown one with the allowed values (E05-S2 spec S2-D5).
 */
public record AgreementCreateCommand(String leadId, String unitId, String transactionType, LocalDate startDate,
                                     LocalDate endDate) {

    public AgreementCreateCommand {
        Objects.requireNonNull(leadId, "leadId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(transactionType, "transactionType");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
    }
}
