package pl.aiwcorpo.mcp.example.tools;

import java.util.List;
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
 * quotes every value, so a text with a quote, a bracket or {@code OR project = HR} stays one literal. Conditions may
 * only use the fields the contracts fix ({@code text}, {@code status}, {@code assignee}, {@code summary}) and the
 * operators {@code EQUALS}, {@code CONTAINS}, {@code NOT_EQUALS}, so {@code project} is not a condition: the requested
 * project narrows the scope part of the query ({@link QueryScope#narrowedTo}, intersected with the link). Where the system applies the user's own rights (Jira does), this is
 * all the scope the query needs.
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** Empty when there is nothing to ask the system. */
    static Optional<String> of(QueryScope scope, String project, String text) {
        QueryScope narrowed = scope.narrowedTo(List.of(project));
        if (narrowed.linkIds().orElseThrow().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(narrowed.jql(Set.of("text"))
                .where("text", Operator.CONTAINS, text)
                .build());
    }
}
