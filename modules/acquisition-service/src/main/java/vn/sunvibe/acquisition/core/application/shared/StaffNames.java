package vn.sunvibe.acquisition.core.application.shared;

import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks up the full names shown next to staff ids in lead and territory responses
 * (acquisition-owner-lead-queue-ui C-18, C-35). A service collects every id of its whole answer first and calls
 * {@link #lookup} once, so a response never costs more than one name query.
 * <p>
 * Only for ids read from acquisition rows the caller may already see; names are never logged.
 */
public final class StaffNames {

    private final LeadAssigneeDirectoryPort directory;

    public StaffNames(LeadAssigneeDirectoryPort directory) {
        this.directory = directory;
    }

    /**
     * The name of each distinct, non-null id that has a user row. Nulls are dropped and ids deduplicated; with no id
     * left the port is not called. An id without a name is absent from the map.
     */
    public Map<String, String> lookup(Collection<String> ids) {
        Set<String> wanted = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null) {
                wanted.add(id);
            }
        }
        return wanted.isEmpty() ? Map.of() : directory.findDisplayNames(wanted);
    }

    /** The staff ids a history entry holds: actor, previous assignee, new assignee (each may be null). */
    public static List<String> idsOf(OwnerLeadActivity activity) {
        return Arrays.asList(activity.getActorUserId(), activity.getFromAssigneeId(), activity.getToAssigneeId());
    }

    /** The name of the id, or null when there is no id or no name. */
    public static String nameOf(Map<String, String> names, String id) {
        return id == null ? null : names.get(id);
    }
}
