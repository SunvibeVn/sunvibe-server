package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The agreement aggregate and its versions (brokerage-agreement BR-1, BR-5; E05-S2 spec section 3.1, 3.2, S2-D3).
 */
class BrokerageAgreementTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-11-21T03:15:00Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));

    @Test
    void create_firstDraft_draftingWithVersionOneDraft() {
        BrokerageAgreement agreement = BrokerageAgreement.create("A1", "U1", "P101", "P1",
                TransactionType.LONG_TERM_RENT, "O1", "L1", "I1", "V1", PERIOD, "S1", NOW);

        assertEquals("A1", agreement.getId());
        assertEquals("U1", agreement.getUnitId());
        assertEquals("P101", agreement.getUnitCodeOrNull());
        assertEquals("P1", agreement.getPropertyId());
        assertEquals(TransactionType.LONG_TERM_RENT, agreement.getTransactionType());
        assertEquals("O1", agreement.getOwnerUserId());
        assertEquals("L1", agreement.getBasisLeadId());
        assertEquals(AgreementStatus.DRAFTING, agreement.getStatus());
        assertEquals("S1", agreement.getCreatedByUserId());
        assertEquals(NOW, agreement.getCreatedAt());
        assertEquals(NOW, agreement.getUpdatedAt());
        assertNull(agreement.getRowVersionOrNull());
        assertEquals(1, agreement.getVersions().size());
        AgreementVersion version = agreement.getVersions().get(0);
        assertEquals("V1", version.getId());
        assertEquals(1, version.getVersionNo());
        assertEquals(VersionStatus.DRAFT, version.getStatus());
        assertEquals(PERIOD, version.getPeriod());
        assertEquals("L1", version.getBasisLeadId());
        assertEquals("I1", version.getBasisIntakeId());
        assertEquals("S1", version.getCreatedByUserId());
        assertEquals(NOW, version.getCreatedAt());
        assertEquals(NOW, version.getUpdatedAt());
    }

    @Test
    void load_anyStoredState_keepsIt() {
        AgreementVersion first = version("V1", 1, VersionStatus.EFFECTIVE, "L1");
        AgreementVersion second = version("V2", 2, VersionStatus.DRAFT, "L2");

        BrokerageAgreement agreement = load(AgreementStatus.ACTIVE, "L2", List.of(first, second));

        assertEquals(AgreementStatus.ACTIVE, agreement.getStatus());
        assertEquals("L2", agreement.getBasisLeadId());
        assertEquals(4L, agreement.getRowVersionOrNull());
        assertEquals(LATER, agreement.getUpdatedAt());
        assertEquals(List.of(first, second), agreement.getVersions());
        assertNull(BrokerageAgreement.load("A1", "U1", null, "P1", TransactionType.LONG_TERM_RENT, "O1", "L1",
                AgreementStatus.DRAFTING, "S1", NOW, NOW, 0L, List.of(version("V1", 1, VersionStatus.DRAFT, "L1")))
                .getUnitCodeOrNull());
    }

    @Test
    void load_brokenRows_refused() {
        assertThrows(IllegalStateException.class, () -> load(AgreementStatus.DRAFTING, "L1", List.of()));
        assertThrows(IllegalStateException.class, () -> load(AgreementStatus.ACTIVE, "L1",
                List.of(version("V1", 1, VersionStatus.EFFECTIVE, "L1"), version("V3", 3, VersionStatus.DRAFT, "L1"))));
        assertThrows(IllegalStateException.class, () -> load(AgreementStatus.ACTIVE, "L1",
                List.of(version("V2", 2, VersionStatus.DRAFT, "L1"), version("V1", 1, VersionStatus.EFFECTIVE, "L1"))));
        assertThrows(IllegalStateException.class, () -> load(AgreementStatus.AWAITING_CONFIRMATION, "L1",
                List.of(version("V1", 1, VersionStatus.DRAFT, "L1"),
                        version("V2", 2, VersionStatus.AWAITING_OWNER, "L1"))));
        assertThrows(IllegalStateException.class, () -> load(AgreementStatus.DRAFTING, "L1",
                List.of(version("V1", 1, VersionStatus.DRAFT, "L2"))));
    }

    private static BrokerageAgreement load(AgreementStatus status, String basisLeadId, List<AgreementVersion> versions) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", basisLeadId,
                status, "S1", NOW, LATER, 4L, versions);
    }

    private static AgreementVersion version(String id, int versionNo, VersionStatus status, String basisLeadId) {
        return AgreementVersion.load(id, versionNo, status, PERIOD, basisLeadId, "I1", "S1", NOW, LATER);
    }
}
