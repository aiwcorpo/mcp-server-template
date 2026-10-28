package pl.aiwcorpo.mcp.example.tools;

import java.util.Optional;
import java.util.Set;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;
import pl.aiwcorpo.mcp.platform.resource.StructuredQuery.Operator;

/**
 * The backend query of {@code search_records} (CQL), built by the server from the scope of the policy and the values
 * the model supplied; the model never supplies a fragment of the query language. {@code new-server.sh} replaces this
 * file with the one of the generated system.
 *
 * <p>{@link QueryScope#cql} adds the narrowing of the virtual server link (a {@code space in (...)} clause) and quotes
 * and escapes every value. The requested space is a filter too, so a space outside the link yields an empty result.
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** Empty when there is nothing to ask the system. */
    static Optional<String> of(QueryScope scope, String project, String text) {
        return Optional.of(scope.cql(Set.of("space", "title"))
                .where("space", Operator.EQUALS, project)
                .where("title", Operator.CONTAINS, text)
                .build());
    }
}
