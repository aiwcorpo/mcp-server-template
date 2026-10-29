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
 * every value. Conditions may only use {@code text}, {@code title} and {@code label} with {@code EQUALS},
 * {@code CONTAINS} or {@code NOT_EQUALS}, so {@code space} is not a condition: the requested space is applied to the
 * rows by the use case.
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** Empty when there is nothing to ask the system. */
    static Optional<String> of(QueryScope scope, String project, String text) {
        return Optional.of(scope.cql(Set.of("title"))
                .where("title", Operator.CONTAINS, text)
                .build());
    }
}
