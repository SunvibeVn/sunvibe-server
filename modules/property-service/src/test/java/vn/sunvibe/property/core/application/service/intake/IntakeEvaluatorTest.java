package vn.sunvibe.property.core.application.service.intake;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mark kinds that need reconciliation (property-create intake D5-12, BR-20), read null-safe (SV-215). */
class IntakeEvaluatorTest {

    @Test
    void reconciliationKind_wrongAssetOrOwnerOnly() {
        assertTrue(IntakeEvaluator.reconciliationKind("WRONG_ASSET"));
        assertTrue(IntakeEvaluator.reconciliationKind("WRONG_OWNER"));
        Arrays.asList("WRONG_SCOPE", "PHOTO_PROBLEM", "OTHER", "UNKNOWN", null)
                .forEach(kind -> assertFalse(IntakeEvaluator.reconciliationKind(kind), String.valueOf(kind)));
    }
}
