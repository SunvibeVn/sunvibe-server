package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * What a manager reads before calling the lead's phone (property-create intake spec 4.6 step 6, 7.1). The basis is kept for
 * managers, so it stays out of every log; the phrase is never here.
 *
 * @param basis null until a start
 */
public record OwnerCheckContext(String proofId, String accountAddressMasked, long accountAgeDays, Instant confirmedAt,
                                @Schema(nullable = true, description = "Null until a start") String basis, Signals signals,
                                List<Refusal> refusals) {

    /**
     * @param accountProofs         the account's live proofs
     * @param accountRefusals       its proofs counted as refused, all time
     * @param creatorProofs         the account's proofs whose request this proof's creator made
     * @param creatorRefusals       every proof of that creator counted as refused
     * @param otherOpenLeadsOfPhone the other open leads of the lead's phone the manager can see
     * @param otherOwnersOnPhone    those whose live proof or result names another user
     */
    @Schema(name = "OwnerCheckSignals")
    public record Signals(long accountProofs, long accountRefusals, long creatorProofs, long creatorRefusals,
                          long otherOpenLeadsOfPhone, long otherOwnersOnPhone) {
    }

    @Schema(name = "OwnerCheckRefusal")
    public record Refusal(Instant checkedAt, String basis) {

        @Override
        public String toString() {
            return "Refusal[" + checkedAt + "]";
        }
    }

    @Override
    public String toString() {
        return "OwnerCheckContext[" + proofId + "]";
    }
}
