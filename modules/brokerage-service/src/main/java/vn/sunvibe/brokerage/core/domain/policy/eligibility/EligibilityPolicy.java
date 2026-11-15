package vn.sunvibe.brokerage.core.domain.policy.eligibility;

import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Whether an agreement could be drafted on one unit now, and which groups of reasons stop it (brokerage-agreement US-1
 * AC-1, AC-2). Each source is reported for itself, with no deduplication across sources: one cause can show in several
 * groups. A value this policy does not know stops the unit (fail-closed).
 */
public final class EligibilityPolicy {

    private static final String USABLE = "USABLE";
    private static final String PROPERTY_STOP = "PROPERTY_STOP";
    private static final String AVAILABILITY_DIFFERS = "AVAILABILITY_DIFFERS";
    private static final String OWNER_RELATION = "OWNER";
    private static final Set<String> ACCEPTED_VERIFICATIONS = Set.of("UNVERIFIED", "VERIFIED");
    private static final Set<String> DRAFTER_ROLES = Set.of("ROLE_SALE", "ROLE_SALE_LEADER");

    private EligibilityPolicy() {
    }

    /**
     * The intake basis of a lead: its latest COMPLETED intake.
     *
     * @param state the readiness state of that intake (USABLE, SUSPENDED, ENDED); any other value, null included, is
     *              not usable
     * @param units the units the intake maps, each with its readiness reasons
     */
    public record Basis(String state, List<BasisUnit> units) {

        public Basis {
            units = List.copyOf(units);
        }
    }

    /** One unit the basis maps, with the reasons of its readiness. */
    public record BasisUnit(String unitId, List<String> reasons) {

        public BasisUnit {
            reasons = List.copyOf(reasons);
        }
    }

    /**
     * The owner facts of the basis's property.
     *
     * @param declaredOwnerIdOrNull the property's declared owner; null when it has none
     * @param holdings              every ownership row of the property valid today, any relation and verification
     * @param accountEligible       the declared owner's account can sign in and holds no staff role
     */
    public record Owner(String declaredOwnerIdOrNull, List<Holding> holdings, boolean accountEligible) {

        public Owner {
            holdings = List.copyOf(holdings);
        }
    }

    /** One ownership row valid today, its values as stored. */
    public record Holding(String userId, String relation, String verification) {
    }

    /** The reason groups of the unit, each at most once, in the order of {@link ReasonGroup}; empty when eligible. */
    public static List<ReasonGroup> reasonGroups(String unitId, Basis basis, Owner owner) {
        Set<ReasonGroup> groups = EnumSet.noneOf(ReasonGroup.class);
        BasisUnit unit = basis.units().stream().filter(candidate -> candidate.unitId().equals(unitId)).findFirst()
                .orElse(null);
        // brokerage-agreement BR-2: an agreement rests on a usable intake result mapped to the unit.
        if (!USABLE.equals(basis.state()) || unit == null) {
            groups.add(ReasonGroup.BASIS);
        }
        if (unit != null) {
            for (String reason : unit.reasons()) {
                // brokerage-agreement BR-3: a whole-asset stop is PROPERTY; availability, AVAILABILITY_DIFFERS
                // included, stops nothing (D9-18); this lead's unit suspension, another lead's unit stop and any
                // reason this policy does not know stop the unit.
                if (PROPERTY_STOP.equals(reason)) {
                    groups.add(ReasonGroup.PROPERTY);
                } else if (!AVAILABILITY_DIFFERS.equals(reason)) {
                    groups.add(ReasonGroup.UNIT);
                }
            }
        }
        if (!ownerPasses(owner)) {
            groups.add(ReasonGroup.OWNER);
        }
        return List.copyOf(groups);
    }

    /**
     * Whether the viewer is the one who drafts on this lead (brokerage-agreement BR-16): the lead's current assignee,
     * holding SALE or SALE_LEADER.
     */
    public static boolean assignedSale(String assigneeOrNull, String viewerUserId, Set<String> expandedAuthorities) {
        return assigneeOrNull != null && assigneeOrNull.equals(viewerUserId)
                && expandedAuthorities.stream().anyMatch(DRAFTER_ROLES::contains);
    }

    /**
     * brokerage-agreement BR-4: the owner party is the property's declared owner who also holds the current OWNER
     * relation, UNVERIFIED accepted, as one eligible individual. Every row valid today must be an OWNER row of that user
     * with an accepted verification, and there is at least one; a co-owner, a representative, another user's row, a
     * rejected row or an unknown value stops it.
     */
    private static boolean ownerPasses(Owner owner) {
        String declared = owner.declaredOwnerIdOrNull();
        if (declared == null || owner.holdings().isEmpty() || !owner.accountEligible()) {
            return false;
        }
        return owner.holdings().stream().allMatch(holding -> declared.equals(holding.userId())
                && OWNER_RELATION.equals(holding.relation())
                && holding.verification() != null && ACCEPTED_VERIFICATIONS.contains(holding.verification()));
    }
}
