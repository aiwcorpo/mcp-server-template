package pl.aiwcorpo.mcp.example.tools;

import java.util.Optional;
import java.util.Set;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;
import pl.aiwcorpo.mcp.platform.resource.StructuredQuery.Operator;

/**
 * The backend query of {@code search_records}, built by the server from the scope of the policy and the values the
 * model supplied; the model never supplies a fragment of the query language. {@code new-server.sh} replaces this file
 * with the one of the generated system; the Jira variant (JQL) is the template's own.
 *
 * <p>{@link QueryScope#jql} adds the narrowing of the virtual server link (a {@code project in (...)} clause) and
 * quotes and escapes every value, so a text with a quote, a bracket or {@code OR project = HR} stays one literal.
 * The requested project is a filter too, so a project outside the link yields an empty result from the system itself.
 * Where the system applies the user's own rights (Jira does), this is all the scope the query needs.
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** Empty when there is nothing to ask the system. */
    static Optional<String> of(QueryScope scope, String project, String text) {
        return Optional.of(scope.jql(Set.of("project", "text"))
                .where("project", Operator.EQUALS, project)
                .where("text", Operator.CONTAINS, text)
                .build());
    }
}
