package pl.aiwcorpo.mcp.example.core.domain;

/**
 * A record of the backend system, reduced to the fields the tools and the resource policy need. Anything else the
 * backend returns stays in the adapter: a field the model never sees cannot leak.
 *
 * @param id                    id as the backend issues it
 * @param project               the container the record lives in (project, space, group or queue), as the backend
 *                              reports it now; never taken from a tool argument
 * @param projectClassification classification of that container ({@code ""} = none), {@code null} = the backend did
 *                              not say; the policy then refuses the record
 * @param status                {@code null} when the backend has none
 */
public record ExampleRecord(String id, String project, String projectClassification, String title,
        String status) {

    public ExampleRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("project must not be blank");
        }
    }
}
