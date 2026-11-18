package vn.sunvibe.property.core.application.usecase.intake;

import java.util.List;

/**
 * Where a COMPLETED intake came from (brokerage-agreement E05-S3 spec S3-D17).
 *
 * @param sourceRevisionId the survey revision the intake was built from
 * @param units            each mapped unit with the surveyed space it came from, in the order of the mapping rows
 */
public record IntakeOriginView(String leadId, int intentCycle, String sourceRevisionId, List<UnitSpace> units) {

    public IntakeOriginView {
        units = List.copyOf(units);
    }

    /** One mapping row: the unit and the surveyed space it came from. */
    public record UnitSpace(String unitId, String spaceId) {
    }
}
