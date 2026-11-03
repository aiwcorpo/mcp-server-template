package pl.aiwcorpo.mcp.example.core.domain;

/**
 * The object does not exist, does not belong to the record it was asked under, or the caller may not see it. The cases
 * are one exception on purpose: the client gets the same {@code not_found_or_denied} for all of them, so it cannot
 * probe which ids exist.
 */
public class NotFoundOrDeniedException extends RuntimeException {

    public NotFoundOrDeniedException() {
        super("not found or not visible", null, false, false);
    }
}
