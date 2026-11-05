package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;

import java.util.List;
import java.util.Optional;

/**
 * Intake rows (property-create intake spec 5.2, 5.3). Every read answers the stored row as it stands now: open-in-view
 * cannot leave an old state behind, because no entity stays managed.
 */
public interface InventoryIntakeDomainRepository {

    /**
     * Inserts and flushes.
     *
     * @throws vn.sunvibe.property.core.domain.exception.IntakeConflictException an open row of the cycle, or the number,
     *                                                                           already exists (I-1)
     */
    void insert(InventoryIntake intake);

    /** Writes the row and flushes. */
    void update(InventoryIntake intake);

    Optional<InventoryIntake> findById(String id);

    /** The row under a FOR NO KEY UPDATE lock held to the end of the transaction, read after the lock is taken. */
    Optional<InventoryIntake> lockById(String id);

    List<InventoryIntake> findByLeadId(String leadId);

    /** The COMPLETED rows whose result is this property: a winner of a case can name it that way (spec 4.11). */
    List<InventoryIntake> findCompletedByResult(String propertyId);

    /** The open rows of a ward: the competing intakes of the signal (spec 4.10). */
    List<InventoryIntake> findOpenOfWard(String wardCode);

    /** The highest number of the lead plus one; only under the lead lock is it safe to use. */
    int nextIntakeNo(String leadId);
}
