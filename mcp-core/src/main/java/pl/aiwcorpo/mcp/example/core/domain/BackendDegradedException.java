package pl.aiwcorpo.mcp.example.core.domain;

/**
 * The backend reports a version this build was not tested with, so the server only serves reads. Carries no text from
 * the backend: the client sees a fixed {@code upstream_unavailable}.
 */
public class BackendDegradedException extends RuntimeException {

    public BackendDegradedException() {
        super("backend version not supported for changes", null, false, false);
    }
}
