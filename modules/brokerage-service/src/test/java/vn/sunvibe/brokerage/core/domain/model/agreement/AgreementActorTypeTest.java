package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who acted on a history row (E05-S7 spec S7-D7): no actor is Sunvibe's own job, the agreement's owner is the owner,
 * anyone else is staff (brokerage-agreement BR-4: an owner account holds no staff role).
 */
class AgreementActorTypeTest {

    private static final String OWNER = "01JOWNER000000000000000000";

    @Test
    void of_noActor_system() {
        assertEquals(AgreementActorType.SYSTEM, AgreementActorType.of(null, OWNER));
    }

    @Test
    void of_theOwner_owner() {
        assertEquals(AgreementActorType.OWNER, AgreementActorType.of(OWNER, OWNER));
    }

    @Test
    void of_anotherUser_staff() {
        assertEquals(AgreementActorType.STAFF, AgreementActorType.of("01JSTAFF000000000000000000", OWNER));
    }
}
