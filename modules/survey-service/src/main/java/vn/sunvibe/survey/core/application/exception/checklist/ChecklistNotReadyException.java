package vn.sunvibe.survey.core.application.exception.checklist;

import java.io.Serial;
import java.util.List;

/** Gates S3..S7 (survey-checklist submission spec 4.4, 7.3): one field per failing gate, in gate order. */
public class ChecklistNotReadyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<String> fields;

    public ChecklistNotReadyException(List<String> fields) {
        super("The checklist cannot be submitted yet.");
        this.fields = List.copyOf(fields);
    }

    public List<String> fields() {
        return fields;
    }
}
