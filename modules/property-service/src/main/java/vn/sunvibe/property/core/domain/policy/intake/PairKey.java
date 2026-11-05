package vn.sunvibe.property.core.domain.policy.intake;

/**
 * The key of a duplicate pair (property-create intake spec 4.10): the scope is the first 16 hex characters of its hash, so a
 * new selection is a new pair, and the ids of two intakes go in byte order, so either side names the same pair.
 */
public final class PairKey {

    private static final int SCOPE_LENGTH = 16;

    private PairKey() {
    }

    public static String property(String propertyId, String intakeId, String scopeHash) {
        return "P:" + propertyId + ":" + intakeId + ":" + scopeHash.substring(0, SCOPE_LENGTH);
    }

    public static String linkPlace(String propertyId, String intakeId, String scopeHash) {
        return "L:" + propertyId + ":" + intakeId + ":" + scopeHash.substring(0, SCOPE_LENGTH);
    }

    public static String intakes(String firstId, String firstScopeHash, String secondId, String secondScopeHash) {
        boolean inOrder = firstId.compareTo(secondId) < 0;
        String lower = inOrder ? firstId : secondId;
        String higher = inOrder ? secondId : firstId;
        String lowerScope = inOrder ? firstScopeHash : secondScopeHash;
        String higherScope = inOrder ? secondScopeHash : firstScopeHash;
        return "I:" + lower + ":" + higher + ":" + lowerScope.substring(0, SCOPE_LENGTH) + ":"
                + higherScope.substring(0, SCOPE_LENGTH);
    }
}
