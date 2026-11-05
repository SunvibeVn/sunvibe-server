package vn.sunvibe.property.core.presentation.dto.request.intake;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A manager's ruling on a case (property-create intake spec 7.2). The use case checks the values and the basis, which is kept for
 * managers, so {@code toString} does not show it.
 */
public record CaseDecisionRequest(@Size(max = 40) String outcome,
                                  @Pattern(regexp = "[0-9A-Za-z]*") @Size(max = 26) String proceedingIntakeId,
                                  @Size(max = 4000) String basis, Long expectedVersion) {

    @Override
    public String toString() {
        return "CaseDecisionRequest[" + outcome + "]";
    }
}
