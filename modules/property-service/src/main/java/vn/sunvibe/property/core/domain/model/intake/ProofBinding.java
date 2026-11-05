package vn.sunvibe.property.core.domain.model.intake;

/** What a proof is bound to (property-create intake I-3, D5-11): one lead, cycle, revision and scope. */
public record ProofBinding(String leadId, int intentCycle, String checklistId, String scopeHash) {
}
