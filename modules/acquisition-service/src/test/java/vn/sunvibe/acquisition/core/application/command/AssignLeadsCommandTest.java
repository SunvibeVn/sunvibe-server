package vn.sunvibe.acquisition.core.application.command;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The shape of one bulk assignment (acquisition-owner-lead-bulk-assign BR-3, D-4): 1 to 20 leads, none of them twice
 * (exact string), refused on the field {@code leads} with a message that never names a lead; the note is staff text and
 * is never printed.
 */
class AssignLeadsCommandTest {

    private static final String ASSIGNEE = "01JSALEBBBBBBBBBBBBBBBBBBB";
    private static final String LEAD = "01JLEADAAAAAAAAAAAAAAAAAAA";

    @Test
    void new_duplicateLeadId_isRefusedOnLeads() {
        List<AssignLeadsCommand.LeadInput> twice = List.of(new AssignLeadsCommand.LeadInput(LEAD, 0L),
                new AssignLeadsCommand.LeadInput(LEAD, 3L));

        InvalidLeadCommandException refused = assertThrows(InvalidLeadCommandException.class,
                () -> new AssignLeadsCommand(ASSIGNEE, null, twice));

        assertEquals("leads", refused.getField());
        assertFalse(refused.getMessage().contains(LEAD), refused.getMessage());
        List<AssignLeadsCommand.LeadInput> byCase = List.of(new AssignLeadsCommand.LeadInput(LEAD, 0L),
                new AssignLeadsCommand.LeadInput(LEAD.toLowerCase(java.util.Locale.ROOT), 0L));
        assertEquals(2, new AssignLeadsCommand(ASSIGNEE, null, byCase).leads().size());
    }

    @Test
    void new_emptyOrOver20_isRefusedOnLeads() {
        assertEquals("leads", assertThrows(InvalidLeadCommandException.class,
                () -> new AssignLeadsCommand(ASSIGNEE, null, List.of())).getField());
        assertEquals("leads", assertThrows(InvalidLeadCommandException.class,
                () -> new AssignLeadsCommand(ASSIGNEE, null, inputs(21))).getField());

        assertEquals(1, new AssignLeadsCommand(ASSIGNEE, null, inputs(1)).leads().size());
        assertEquals(20, AssignLeadsCommand.MAX_LEADS);
        List<AssignLeadsCommand.LeadInput> twenty = new ArrayList<>(inputs(20));
        AssignLeadsCommand command = new AssignLeadsCommand(ASSIGNEE, null, twenty);
        twenty.clear();
        assertEquals(20, command.leads().size());
    }

    @Test
    void toString_hidesTheNote() {
        AssignLeadsCommand command = new AssignLeadsCommand(ASSIGNEE, "secret-note", inputs(1));

        assertFalse(command.toString().contains("secret-note"), command.toString());
    }

    /** Distinct ids of 26 characters, version 0. */
    private static List<AssignLeadsCommand.LeadInput> inputs(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new AssignLeadsCommand.LeadInput(String.format("01JLEAD%019d", i), 0L))
                .toList();
    }
}
