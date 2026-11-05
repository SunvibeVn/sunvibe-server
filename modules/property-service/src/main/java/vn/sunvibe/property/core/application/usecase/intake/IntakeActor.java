package vn.sunvibe.property.core.application.usecase.intake;

import vn.sunvibe.property.core.application.shared.external.lead.IntakeViewer;

/**
 * The staff member a command or read runs for (property-create intake spec 4.3).
 *
 * @param viewer   what acquisition lets them see
 * @param saleRole they hold SALE or SALE_LEADER, the roles an assignee acts in; a manager role alone does not
 * @param manager  they hold SALE_LEADER, SALES_MANAGER or ADMIN (SUPER_ADMIN by the hierarchy)
 */
public record IntakeActor(IntakeViewer viewer, boolean saleRole, boolean manager) {

    public String userId() {
        return viewer.userId();
    }
}
