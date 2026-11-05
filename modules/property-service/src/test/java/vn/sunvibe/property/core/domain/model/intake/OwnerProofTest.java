package vn.sunvibe.property.core.domain.model.intake;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** The proof of property-create intake I-3, D5-7, D5-10 and the request of 4.6. */
class OwnerProofTest {

    private static final Instant NOW = Instant.parse("2026-11-06T02:00:00Z");
    private static final ProofBinding BINDING = new ProofBinding("01LEAD", 1, "01CHECKLIST", "scope");

    @Test
    void proof_liveWhileEverythingMatches() {
        assertTrue(proof().isLive(BINDING, 2));
    }

    /** D5-10: losing sign-in kills every proof made before it, even if the account is back. */
    @Test
    void proof_lossCountChanged_notLive() {
        assertFalse(proof().isLive(BINDING, 3));
    }

    @Test
    void proof_otherBinding_notLive() {
        OwnerProof proof = proof();

        assertFalse(proof.isLive(new ProofBinding("01OTHER", 1, "01CHECKLIST", "scope"), 2));
        assertFalse(proof.isLive(new ProofBinding("01LEAD", 2, "01CHECKLIST", "scope"), 2));
        assertFalse(proof.isLive(new ProofBinding("01LEAD", 1, "01OTHER", "scope"), 2));
        assertFalse(proof.isLive(new ProofBinding("01LEAD", 1, "01CHECKLIST", "other"), 2));
    }

    @Test
    void proof_withdrawnOrVoided_notLive() {
        OwnerProof withdrawn = proof();
        withdrawn.withdraw(NOW);
        OwnerProof voided = proof();
        voided.voidNow(NOW);

        assertFalse(withdrawn.isLive(BINDING, 2));
        assertFalse(voided.isLive(BINDING, 2));
        assertFalse(withdrawn.inForce());
    }

    @Test
    void check_startThenEntry_done_startingManagerKept_hashCleared_expiryKept() {
        OwnerProof proof = proof();
        Instant expires = NOW.plusSeconds(600);
        proof.startCheck("hash", expires, "01MANAGER", "Called first", NOW);

        assertTrue(proof.phraseOpen(NOW));
        proof.checkDone(NOW);

        assertEquals(OwnerCheckState.DONE, proof.getOwnerCheck());
        assertEquals("01MANAGER", proof.getOwnerCheckByUserId());
        assertNull(proof.getPhraseHash());
        assertEquals(expires, proof.getPhraseExpiresAt());
        assertFalse(proof.phraseOpen(NOW));
    }

    @Test
    void check_startAgain_replacesPhraseAndZeroesCounter() {
        OwnerProof proof = proof();
        proof.startCheck("first", NOW.plusSeconds(600), "01MANAGER", "Called first", NOW);
        proof.countWrongPhrase();

        proof.startCheck("second", NOW.plusSeconds(900), "01OTHER", "Called again", NOW);

        assertEquals("second", proof.getPhraseHash());
        assertEquals(0, proof.getPhraseFailedAttempts());
        assertEquals("01OTHER", proof.getOwnerCheckByUserId());
    }

    @Test
    void check_phraseExpired_notOpen() {
        OwnerProof proof = proof();
        proof.startCheck("hash", NOW.plusSeconds(600), "01MANAGER", "Called", NOW);

        assertFalse(proof.phraseOpen(NOW.plusSeconds(600)));
        assertTrue(proof.phraseOpen(NOW.plusSeconds(599)));
    }

    /** spec 4.6 step 6: the fifth wrong entry ends the phrase; the proof stays. */
    @Test
    void check_fifthWrong_endsPhrase_proofStays() {
        OwnerProof proof = proof();
        proof.startCheck("hash", NOW.plusSeconds(600), "01MANAGER", "Called", NOW);

        for (int i = 0; i < 4; i++) {
            proof.countWrongPhrase();
            assertTrue(proof.phraseOpen(NOW));
        }
        proof.countWrongPhrase();

        assertFalse(proof.phraseOpen(NOW));
        assertTrue(proof.inForce());
        assertEquals(OwnerCheckState.REQUIRED, proof.getOwnerCheck());
    }

    @Test
    void refusal_voidsAndOverwrites() {
        OwnerProof proof = proof();
        proof.startCheck("hash", NOW.plusSeconds(600), "01MANAGER", "Before the call", NOW);

        proof.refuse("01OTHER", "A stranger answered", NOW.plusSeconds(60));

        assertEquals(OwnerCheckState.REFUSED, proof.getOwnerCheck());
        assertFalse(proof.inForce());
        assertNull(proof.getPhraseHash());
        assertEquals("01OTHER", proof.getOwnerCheckByUserId());
        assertEquals("A stranger answered", proof.getOwnerCheckBasis());
        assertEquals(NOW.plusSeconds(60), proof.getOwnerCheckAt());
    }

    /** spec 4.6 step 6: REFUSED, or no longer live after a start without DONE, counts as refused in the signals. */
    @Test
    void countsAsRefused_rules() {
        OwnerProof refused = proof();
        refused.refuse("01MANAGER", "Stranger", NOW);
        OwnerProof startedThenWithdrawn = proof();
        startedThenWithdrawn.startCheck("hash", NOW.plusSeconds(600), "01MANAGER", "Called", NOW);
        startedThenWithdrawn.withdraw(NOW);
        OwnerProof startedThenDone = proof();
        startedThenDone.startCheck("hash", NOW.plusSeconds(600), "01MANAGER", "Called", NOW);
        startedThenDone.checkDone(NOW);
        startedThenDone.withdraw(NOW);
        OwnerProof neverStarted = proof();
        neverStarted.withdraw(NOW);

        assertTrue(refused.countsAsRefused(false));
        assertTrue(startedThenWithdrawn.countsAsRefused(false));
        assertFalse(startedThenDone.countsAsRefused(false));
        assertFalse(neverStarted.countsAsRefused(false));
        assertFalse(proof().countsAsRefused(true));
    }

    @Test
    void request_openUntilItEnds() {
        assertTrue(request().isOpen(NOW));
        assertFalse(request().isOpen(NOW.plusSeconds(3600)));
    }

    @Test
    void request_voidDeclineAndConsume_notOpen() {
        OwnerRequest voided = request();
        voided.voidNow(NOW);
        OwnerRequest declined = request();
        declined.decline(NOW);
        OwnerRequest consumed = request();
        consumed.consume(NOW);

        assertFalse(voided.isOpen(NOW));
        assertFalse(declined.isOpen(NOW));
        assertFalse(consumed.isOpen(NOW));
        assertNull(voided.getDisplaySnapshot(), "a dead request keeps no address");
        assertNull(declined.getDisplaySnapshot());
        assertEquals("{}", consumed.getDisplaySnapshot(), "a confirmed item still shows its snapshot");
    }

    @Test
    void toString_holdsNoHashOrSnapshot() {
        assertFalse(request().toString().contains("{}"));
        assertFalse(request().toString().contains("codehash"));
        assertFalse(proof().toString().contains("scope"));
    }

    private static OwnerProof proof() {
        return OwnerProof.create("01PROOF", "01INTAKE", "01REQUEST", BINDING, "01OWNER", "owner-link-2026-11", 2, NOW);
    }

    private static OwnerRequest request() {
        return OwnerRequest.create("01REQUEST", "01INTAKE", "01OWNER", "codehash", "{}", NOW.plusSeconds(60), "01SALE", NOW);
    }
}
