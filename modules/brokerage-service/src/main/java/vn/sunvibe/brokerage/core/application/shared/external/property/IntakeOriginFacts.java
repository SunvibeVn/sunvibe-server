package vn.sunvibe.brokerage.core.application.shared.external.property;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a COMPLETED intake came from, read at one moment (E05-S3 spec S3-D11).
 *
 * @param sourceRevisionId the survey revision the intake was built from
 * @param spaceIdByUnitId  the surveyed space each mapped unit came from, unmodifiable, in mapping order
 */
public record IntakeOriginFacts(String leadId, int intentCycle, String sourceRevisionId,
                                Map<String, String> spaceIdByUnitId) {

    public IntakeOriginFacts {
        spaceIdByUnitId = Collections.unmodifiableMap(new LinkedHashMap<>(spaceIdByUnitId));
    }
}
