package pl.aiwcorpo.mcp.example.tools;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;
import pl.aiwcorpo.mcp.platform.resource.StructuredQuery.Operator;

/**
 * The backend query of {@code search_records} (CQL), built by the server from the scope of the policy and the values
 * the model supplied; the model never supplies a fragment of the query language. {@code new-server.sh} replaces this
 * file with the one of the generated system.
 *
 * <p>{@link QueryScope#cql} adds the narrowing to the requested space (a {@code space in (...)} clause) and quotes
 * every value. Conditions may only use {@code text}, {@code title} and {@code label} with {@code EQUALS},
 * {@code CONTAINS} or {@code NOT_EQUALS}, so {@code space} is not a condition: the requested space narrows the scope part of the query
 * ({@link QueryScope#narrowedTo}).
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** The query for the requested project; the grants are not a condition of a system that applies the user's rights. */
    static Optional<String> of(QueryScope scope, String project, String text) {
        QueryScope narrowed = scope.narrowedTo(List.of(project));
        return Optional.of(narrowed.cql(Set.of("title"))
                .where("title", Operator.CONTAINS, text)
                .build());
    }
}
