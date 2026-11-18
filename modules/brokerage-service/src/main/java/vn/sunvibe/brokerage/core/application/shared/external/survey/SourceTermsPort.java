package vn.sunvibe.brokerage.core.application.shared.external.survey;

import java.util.Optional;

/**
 * Port - the G8 terms of a lead cycle's current survey source, as the survey flattens them (E05-S3 spec S3-D11, S3-D12;
 * ADR-009). Viewer-less: brokerage calls it only after it checked that its caller may see the agreement's basis lead. It
 * joins the caller's transaction, which it needs, and writes nothing.
 */
public interface SourceTermsPort {

    /** The terms of the cycle's current unmarked PASSED revision; empty when the cycle has none. */
    Optional<SourceTermsFacts> currentTerms(String leadId, int intentCycle);
}
