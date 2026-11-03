package vn.sunvibe.survey.core.domain.exception;

import java.io.Serial;

/**
 * A survey photo was asked for a move its status or marks refuse (survey-media spec 4.2). The message holds the move,
 * the status and the photo id only.
 */
public class IllegalMediaTransitionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IllegalMediaTransitionException(String move, String mediaId, String state) {
        super("Survey photo " + mediaId + " cannot " + move + " while " + state + ".");
    }
}
