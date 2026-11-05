package vn.sunvibe.property.core.domain.model.intake;

import java.time.Instant;

/**
 * The account's own confirmation and the manager's check of it (property-create intake spec 4.6, I-3, D5-7, D5-10). It
 * records who controlled the account, not who owns the asset (property-create BR-3). The phrase hash and the basis stay out of every log.
 */
public final class OwnerProof {

    /** A-14: wrong entries that end a phrase. */
    public static final int MAX_WRONG_PHRASES = 5;

    private final String id;
    private final String intakeId;
    private final String requestId;
    private final ProofBinding binding;
    private final String userId;
    private final String statementVersion;
    private final Instant confirmedAt;
    private final int signInLosses;
    private Instant withdrawnAt;
    private Instant voidedAt;
    private OwnerCheckState ownerCheck;
    private String phraseHash;
    private Instant phraseExpiresAt;
    private int phraseFailedAttempts;
    private String ownerCheckByUserId;
    private String ownerCheckBasis;
    private Instant ownerCheckAt;

    private OwnerProof(String id, String intakeId, String requestId, ProofBinding binding, String userId,
                       String statementVersion, Instant confirmedAt, Instant withdrawnAt, Instant voidedAt,
                       OwnerCheckState ownerCheck, int signInLosses, String phraseHash, Instant phraseExpiresAt,
                       int phraseFailedAttempts, String ownerCheckByUserId, String ownerCheckBasis, Instant ownerCheckAt) {
        this.id = id;
        this.intakeId = intakeId;
        this.requestId = requestId;
        this.binding = binding;
        this.userId = userId;
        this.statementVersion = statementVersion;
        this.confirmedAt = confirmedAt;
        this.withdrawnAt = withdrawnAt;
        this.voidedAt = voidedAt;
        this.ownerCheck = ownerCheck;
        this.signInLosses = signInLosses;
        this.phraseHash = phraseHash;
        this.phraseExpiresAt = phraseExpiresAt;
        this.phraseFailedAttempts = phraseFailedAttempts;
        this.ownerCheckByUserId = ownerCheckByUserId;
        this.ownerCheckBasis = ownerCheckBasis;
        this.ownerCheckAt = ownerCheckAt;
    }

    public static OwnerProof create(String id, String intakeId, String requestId, ProofBinding binding, String userId,
                                    String statementVersion, int signInLosses, Instant now) {
        return new OwnerProof(id, intakeId, requestId, binding, userId, statementVersion, now, null, null,
                OwnerCheckState.REQUIRED, signInLosses, null, null, 0, null, null, null);
    }

    public static OwnerProof load(String id, String intakeId, String requestId, ProofBinding binding, String userId,
                                  String statementVersion, Instant confirmedAt, Instant withdrawnAt, Instant voidedAt,
                                  OwnerCheckState ownerCheck, int signInLosses, String phraseHash,
                                  Instant phraseExpiresAt, int phraseFailedAttempts, String ownerCheckByUserId,
                                  String ownerCheckBasis, Instant ownerCheckAt) {
        return new OwnerProof(id, intakeId, requestId, binding, userId, statementVersion, confirmedAt, withdrawnAt, voidedAt,
                ownerCheck, signInLosses, phraseHash, phraseExpiresAt, phraseFailedAttempts, ownerCheckByUserId,
                ownerCheckBasis, ownerCheckAt);
    }

    /** Neither withdrawn nor voided: the row the partial unique index of I-3 counts. */
    public boolean inForce() {
        return withdrawnAt == null && voidedAt == null;
    }

    /** I-3, D5-10: in force, on this binding, and the account has lost no sign-in since. */
    public boolean isLive(ProofBinding current, int accountSignInLosses) {
        return inForce() && binding.equals(current) && signInLosses == accountSignInLosses;
    }

    public void withdraw(Instant now) {
        withdrawnAt = now;
        phraseHash = null;
    }

    public void voidNow(Instant now) {
        if (voidedAt == null) {
            voidedAt = now;
        }
        phraseHash = null;
    }

    /** A new start replaces any open phrase and zeroes its counter (spec 4.6 step 6). */
    public void startCheck(String newPhraseHash, Instant expiresAt, String managerUserId, String basis, Instant now) {
        phraseHash = newPhraseHash;
        phraseExpiresAt = expiresAt;
        phraseFailedAttempts = 0;
        ownerCheckByUserId = managerUserId;
        ownerCheckBasis = basis;
        ownerCheckAt = now;
    }

    public boolean phraseOpen(Instant now) {
        return phraseHash != null && phraseExpiresAt != null && now.isBefore(phraseExpiresAt)
                && phraseFailedAttempts < MAX_WRONG_PHRASES;
    }

    public void countWrongPhrase() {
        phraseFailedAttempts++;
    }

    /** The starting manager stays as who checked; the hash goes and the expiry stays as the record of a start. */
    public void checkDone(Instant now) {
        ownerCheck = OwnerCheckState.DONE;
        ownerCheckAt = now;
        phraseHash = null;
    }

    /** spec 4.6 step 6: the proof is void, and the refusal overwrites who, why and when. */
    public void refuse(String managerUserId, String basis, Instant now) {
        ownerCheck = OwnerCheckState.REFUSED;
        ownerCheckByUserId = managerUserId;
        ownerCheckBasis = basis;
        ownerCheckAt = now;
        voidNow(now);
    }

    /**
     * spec 4.6 step 6: for the signals a proof counts as refused if it is REFUSED, or if it is no longer live after a start
     * without DONE, however it died, so nothing dodges one.
     *
     * @param live whether the proof is live now
     */
    public boolean countsAsRefused(boolean live) {
        return ownerCheck == OwnerCheckState.REFUSED
                || (!live && phraseExpiresAt != null && ownerCheck != OwnerCheckState.DONE);
    }

    public String getId() {
        return id;
    }

    public String getIntakeId() {
        return intakeId;
    }

    public String getRequestId() {
        return requestId;
    }

    public ProofBinding getBinding() {
        return binding;
    }

    public String getUserId() {
        return userId;
    }

    public String getStatementVersion() {
        return statementVersion;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public OwnerCheckState getOwnerCheck() {
        return ownerCheck;
    }

    public int getSignInLosses() {
        return signInLosses;
    }

    public String getPhraseHash() {
        return phraseHash;
    }

    public Instant getPhraseExpiresAt() {
        return phraseExpiresAt;
    }

    public int getPhraseFailedAttempts() {
        return phraseFailedAttempts;
    }

    public String getOwnerCheckByUserId() {
        return ownerCheckByUserId;
    }

    public String getOwnerCheckBasis() {
        return ownerCheckBasis;
    }

    public Instant getOwnerCheckAt() {
        return ownerCheckAt;
    }

    @Override
    public String toString() {
        return "OwnerProof[" + id + " " + ownerCheck + "]";
    }
}
