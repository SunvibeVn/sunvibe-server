package vn.sunvibe.acquisition.core.application.usecase;

import vn.sunvibe.acquisition.core.application.query.SaleTerritoryView;

import java.util.List;

/**
 * Use Case - SaleTerritoryUseCase
 * <p>
 * Which staff member looks after each ward (acquisition-owner-lead-queue BR-5, BR-10): at most one person per ward,
 * and every change is recorded. A change affects only leads sent afterwards (acquisition-owner-lead-queue BR-6).
 */
public interface SaleTerritoryUseCase {

    /**
     * Every ward of the province, assigned or not, sorted by ward name.
     *
     * @throws vn.sunvibe.shared.exception.ResourceNotFoundException the province is not in the catalog
     */
    List<SaleTerritoryView> list(String provinceCode);

    /**
     * Give the ward to the person, replacing whoever had it; the same person again changes nothing.
     *
     * @throws vn.sunvibe.shared.exception.ResourceNotFoundException                     the ward is not in the catalog
     * @throws vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException the person may not hold
     *                                                                                      leads (field userId)
     */
    SaleTerritoryView assign(String wardCode, String userId, String actorId);

    /**
     * Leave the ward without a person; nothing happens when nobody had it.
     *
     * @throws vn.sunvibe.shared.exception.ResourceNotFoundException the ward is not in the catalog
     */
    void unassign(String wardCode, String actorId);
}
