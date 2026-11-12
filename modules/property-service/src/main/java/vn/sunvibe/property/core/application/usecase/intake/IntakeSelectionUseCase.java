package vn.sunvibe.property.core.application.usecase.intake;

import java.util.List;

/** The selection of the parts an intake takes (property-create intake spec 4.5, S-2). */
public interface IntakeSelectionUseCase {

    /**
     * Creates the row, or replaces the selection: a different selection voids what was built on the old one; the same parts
     * again change nothing (property-create SV-202). Answers the block.
     */
    IntakeBlockView select(String leadId, IntakeActor actor, List<String> spaceIds, Long expectedVersion);
}
